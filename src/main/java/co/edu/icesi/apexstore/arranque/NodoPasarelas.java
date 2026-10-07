package co.edu.icesi.apexstore.arranque;

import ApexStore.Pagos.NotificarResultadoPagoPrx;
import ApexStore.Pruebas.ConfigPasarela;
import co.edu.icesi.apexstore.comun.ConfigUtil;
import co.edu.icesi.apexstore.pasarelas.AdminPasarelaI;
import co.edu.icesi.apexstore.pasarelas.ConfigSimulacion;
import co.edu.icesi.apexstore.pasarelas.NotificadorCallback;
import co.edu.icesi.apexstore.pasarelas.PasarelaSimuladaBase;
import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Util;
import java.lang.reflect.Constructor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/** Arranque configurable del nodo de pasarelas simuladas. */
public final class NodoPasarelas {
    private NodoPasarelas() {
    }

    public static void ejecutar(String[] args) {
        String archivo = argumentoConfig(args, "config/pasarelas.properties");
        try (Communicator communicator = Util.initialize(new String[0], archivo);
             ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(8)) {
            var props = communicator.getProperties();
            ObjectAdapter adapter = communicator.createObjectAdapter("Pasarelas");
            NotificarResultadoPagoPrx receptor = NotificarResultadoPagoPrx.uncheckedCast(
                    communicator.propertyToProxy("Pasarela.Receptor.Proxy"));
            int maxIntentos = entero(props, "Pasarela.Callback.MaxIntentos", 5);
            long backoff = entero(props, "Pasarela.Callback.BackoffInicialMs", 100);
            for (String codigo : propiedad(props, "Pasarelas.Habilitadas", "").split(",")) {
                codigo = codigo.trim();
                if (codigo.isEmpty()) {
                    continue;
                }
                ConfigPasarela cfg = new ConfigPasarela(
                        "NORMAL",
                        (int) rango(propiedad(props, "Pasarela." + codigo + ".AckMs", "0-0"))[0],
                        (int) rango(propiedad(props, "Pasarela." + codigo + ".AckMs", "0-0"))[1],
                        (int) rango(propiedad(props, "Pasarela." + codigo + ".CallbackMs", "0-0"))[0],
                        (int) rango(propiedad(props, "Pasarela." + codigo + ".CallbackMs", "0-0"))[1],
                        Double.parseDouble(propiedad(props, "Pasarela." + codigo + ".TasaRechazo", "0")));
                ConfigSimulacion simulacion = new ConfigSimulacion(cfg);
                NotificadorCallback notificador = new NotificadorCallback(receptor, scheduler, maxIntentos, backoff);
                String clase = propiedad(props, "Pasarela." + codigo + ".Clase", "");
                Constructor<?> constructor = Class.forName(clase).getConstructor(
                        String.class, ConfigSimulacion.class, NotificadorCallback.class,
                        ScheduledExecutorService.class);
                PasarelaSimuladaBase pasarela = (PasarelaSimuladaBase) constructor.newInstance(
                        codigo, simulacion, notificador, scheduler);
                String identidad = propiedad(props, "Pasarela." + codigo + ".Identidad",
                        "Estrategia" + codigo);
                adapter.add(pasarela, Util.stringToIdentity(identidad));
                adapter.add(new AdminPasarelaI(pasarela),
                        Util.stringToIdentity("Admin" + codigo));
            }
            adapter.activate();
            communicator.waitForShutdown();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo iniciar el nodo de pasarelas", e);
        }
    }

    private static long[] rango(String valor) {
        return ConfigUtil.rango(valor);
    }

    private static String propiedad(com.zeroc.Ice.Properties props, String clave, String defecto) {
        String valor = props.getProperty(clave);
        return valor == null || valor.isBlank() ? defecto : valor;
    }

    private static int entero(com.zeroc.Ice.Properties props, String clave, int defecto) {
        return Integer.parseInt(propiedad(props, clave, Integer.toString(defecto)));
    }

    private static String argumentoConfig(String[] args, String defecto) {
        for (String arg : args) {
            if (arg.startsWith("--config=")) {
                return arg.substring("--config=".length());
            }
        }
        return defecto;
    }
}

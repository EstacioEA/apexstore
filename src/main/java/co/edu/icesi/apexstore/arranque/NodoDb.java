package co.edu.icesi.apexstore.arranque;

import co.edu.icesi.apexstore.db.PersistirTransaccionI;
import co.edu.icesi.apexstore.db.RepositorioJdbc;
import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Util;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Arranque del nodo de persistencia ICE. */
public final class NodoDb {
    private NodoDb() {
    }

    public static void ejecutar(String[] args) {
        String archivo = argumentoConfig(args, "config/db.properties");
        try (Communicator communicator = Util.initialize(new String[0], archivo)) {
            com.zeroc.Ice.Properties propiedades = communicator.getProperties();
            String url = propiedades.getPropertyWithDefault("Db.Url",
                    "jdbc:h2:file:./data/apexstore;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
            String usuario = propiedades.getPropertyWithDefault("Db.Usuario", "sa");
            String clave = propiedades.getPropertyWithDefault("Db.Clave", "");
            try (RepositorioJdbc repositorio = new RepositorioJdbc(url, usuario, clave)) {
                ObjectAdapter adapter = communicator.createObjectAdapter("Db");
                adapter.add(new PersistirTransaccionI(repositorio),
                        Util.stringToIdentity("DbTransacciones"));
                adapter.activate();
                communicator.waitForShutdown();
            }
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo iniciar el nodo DB", e);
        }
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

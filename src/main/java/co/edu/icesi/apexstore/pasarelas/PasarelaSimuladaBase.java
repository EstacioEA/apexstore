package co.edu.icesi.apexstore.pasarelas;

import ApexStore.Comun.AcuseCobro;
import ApexStore.Comun.EstadoTransaccion;
import ApexStore.Comun.ResultadoPago;
import ApexStore.Comun.SolicitudCobro;
import ApexStore.Comun.SolicitudInvalida;
import ApexStore.Comun.ServicioNoDisponible;
import ApexStore.Pagos.EstrategiaPagos;
import ApexStore.Pruebas.ConfigPasarela;
import com.zeroc.Ice.Current;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Template Method para simular acuse rápido y callback posterior. */
public abstract class PasarelaSimuladaBase implements EstrategiaPagos {
    private final String codigo;
    private final ConfigSimulacion configuracion;
    private final NotificadorCallback notificador;
    private final ScheduledExecutorService scheduler;
    private final Map<String, AcuseCobro> cobros = new ConcurrentHashMap<>();
    private final AtomicLong cobrosRecibidos = new AtomicLong();

    protected PasarelaSimuladaBase(String codigo, ConfigSimulacion configuracion,
                                   NotificadorCallback notificador,
                                   ScheduledExecutorService scheduler) {
        this.codigo = codigo;
        this.configuracion = configuracion;
        this.notificador = notificador;
        this.scheduler = scheduler;
    }

    @Override
    public String codigoMedioPago(Current current) {
        return codigo;
    }

    @Override
    public AcuseCobro iniciarCobro(SolicitudCobro solicitud, Current current)
            throws SolicitudInvalida, ServicioNoDisponible {
        validarComun(solicitud);
        AcuseCobro anterior = cobros.get(solicitud.transaccionId);
        if (anterior != null) {
            return anterior;
        }
        ConfigPasarela cfg = configuracion.obtener();
        if ("CAIDA".equalsIgnoreCase(cfg.modo)) {
            throw new ServicioNoDisponible("Pasarela caída", codigo);
        }
        if ("LENTA".equalsIgnoreCase(cfg.modo)) {
            esperar(cfg.latenciaAckMaxMs);
        } else {
            esperar(aleatorio(cfg.latenciaAckMinMs, cfg.latenciaAckMaxMs));
        }
        validarEspecifico(solicitud);
        boolean aceptado = ThreadLocalRandom.current().nextDouble() >= cfg.tasaRechazo;
        String referencia = aceptado ? referencia(solicitud) : "";
        AcuseCobro resultado = new AcuseCobro(aceptado, referencia,
                aceptado ? "" : "Rechazo simulado");
        AcuseCobro existente = cobros.putIfAbsent(solicitud.transaccionId, resultado);
        if (existente != null) {
            return existente;
        }
        cobrosRecibidos.incrementAndGet();
        if (!"SIN_CALLBACK".equalsIgnoreCase(cfg.modo)) {
            long demora = aleatorio(cfg.latenciaCallbackMinMs, cfg.latenciaCallbackMaxMs);
            if ("CONGESTION".equalsIgnoreCase(cfg.modo)) {
                demora = Math.max(5000, demora);
            }
            ResultadoPago callback = new ResultadoPago(solicitud.transaccionId, solicitud.ordenId,
                    aceptado ? EstadoTransaccion.TxAprobada : EstadoTransaccion.TxRechazada,
                    referencia, aceptado ? "" : "Rechazo simulado", codigo, System.currentTimeMillis());
            scheduler.schedule(() -> notificador.enviar(callback), demora, TimeUnit.MILLISECONDS);
            if ("CALLBACK_DUPLICADO".equalsIgnoreCase(cfg.modo)) {
                scheduler.schedule(() -> notificador.enviar(callback), demora + 1, TimeUnit.MILLISECONDS);
            }
        }
        return resultado;
    }

    public void configurar(ConfigPasarela cfg, Current current) {
        configuracion.configurar(cfg);
    }

    public ConfigPasarela configuracion(Current current) {
        return configuracion.obtener();
    }

    public long cobrosRecibidos(Current current) {
        return cobrosRecibidos.get();
    }

    public long callbacksEnviados(Current current) {
        return notificador.enviados();
    }

    public void reiniciarContadores(Current current) {
        cobros.clear();
        cobrosRecibidos.set(0);
    }

    protected abstract void validarEspecifico(SolicitudCobro solicitud) throws SolicitudInvalida;

    protected abstract String referencia(SolicitudCobro solicitud);

    private void validarComun(SolicitudCobro solicitud) throws SolicitudInvalida {
        if (solicitud == null || solicitud.transaccionId == null || solicitud.transaccionId.isBlank()
                || solicitud.ordenId == null || solicitud.ordenId.isBlank()
                || solicitud.montoMinor <= 0 || solicitud.moneda == null || solicitud.moneda.isBlank()) {
            throw new SolicitudInvalida("Solicitud de cobro inválida");
        }
    }

    private static int aleatorio(int minimo, int maximo) {
        return minimo == maximo ? minimo : ThreadLocalRandom.current().nextInt(minimo, maximo + 1);
    }

    private static void esperar(long millis) throws ServicioNoDisponible {
        try {
            if (millis > 0) {
                Thread.sleep(millis);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServicioNoDisponible("Interrumpida", "pasarela");
        }
    }
}

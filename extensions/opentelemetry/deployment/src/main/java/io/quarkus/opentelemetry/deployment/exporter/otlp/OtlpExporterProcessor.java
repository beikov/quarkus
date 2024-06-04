package io.quarkus.opentelemetry.deployment.exporter.otlp;

import static io.quarkus.opentelemetry.runtime.config.build.ExporterType.Constants.CDI_VALUE;

import java.util.List;
import java.util.function.BooleanSupplier;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;

import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.Type;

import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.*;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.opentelemetry.runtime.config.build.OTelBuildConfig;
import io.quarkus.opentelemetry.runtime.config.build.exporter.OtlpExporterBuildConfig;
import io.quarkus.opentelemetry.runtime.config.runtime.OTelRuntimeConfig;
import io.quarkus.opentelemetry.runtime.config.runtime.exporter.OtlpExporterRuntimeConfig;
import io.quarkus.opentelemetry.runtime.exporter.otlp.LateBoundBatchSpanProcessor;
import io.quarkus.opentelemetry.runtime.exporter.otlp.OTelExporterRecorder;
import io.quarkus.tls.TlsRegistryBuildItem;
import io.quarkus.vertx.core.deployment.CoreVertxBuildItem;

@BuildSteps(onlyIf = OtlpExporterProcessor.OtlpExporterEnabled.class)
public class OtlpExporterProcessor {

    static class OtlpExporterEnabled implements BooleanSupplier {
        OtlpExporterBuildConfig exportBuildConfig;
        OTelBuildConfig otelBuildConfig;

        public boolean getAsBoolean() {
            return otelBuildConfig.enabled() &&
                    otelBuildConfig.traces().enabled().orElse(Boolean.TRUE) &&
                    otelBuildConfig.traces().exporter().contains(CDI_VALUE) &&
                    exportBuildConfig.enabled();
        }
    }

    @SuppressWarnings("deprecation")
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    @Consume(TlsRegistryBuildItem.class)
    void createBatchSpanProcessor(OTelExporterRecorder recorder,
            OTelRuntimeConfig otelRuntimeConfig,
            OtlpExporterRuntimeConfig exporterRuntimeConfig,
            CoreVertxBuildItem vertxBuildItem,
            List<ExternalOtelExporterBuildItem> externalOtelExporterBuildItem,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanBuildItemBuildProducer) {
        if (!externalOtelExporterBuildItem.isEmpty()) {
            // if there is an external exporter, we don't want to create the default one
            return;
        }
        syntheticBeanBuildItemBuildProducer.produce(SyntheticBeanBuildItem
                .configure(LateBoundBatchSpanProcessor.class)
                .types(SpanProcessor.class)
                .setRuntimeInit()
                .scope(Singleton.class)
                .unremovable()
                .addInjectionPoint(ParameterizedType.create(DotName.createSimple(Instance.class),
                        new Type[] { ClassType.create(DotName.createSimple(SpanExporter.class.getName())) }, null))
                .createWith(recorder.batchSpanProcessorForOtlp(otelRuntimeConfig, exporterRuntimeConfig,
                        vertxBuildItem.getVertx()))
                .done());
    }
}

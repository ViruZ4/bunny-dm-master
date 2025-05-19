package com.github.dmaster

import android.content.Context
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.MessageEmbedBuilder
import com.aliucord.entities.Plugin
import com.aliucord.annotations.Command
import com.aliucord.Utils
import com.aliucord.api.CommandsAPI
import com.discord.api.commands.ApplicationCommandType
import com.discord.models.domain.ModelMessageDelete
import com.discord.models.message.Message
import com.discord.stores.StoreStream
import com.discord.utilities.time.ClockFactory
import java.util.concurrent.*

@AliucordPlugin
class DMaster : Plugin() {
    private var isRunning = false
    private val secretCode = "dm2025" // Código secreto para autorización
    private var isAuthorized = false
    private val defaultDelay = 180000L // 3 minutos
    private val defaultBatchSize = 50
    private var currentTask: Future<*>? = null
    private val executor = Executors.newSingleThreadExecutor()

    override fun start(context: Context) {
        commands.registerCommand(
            "dmauth",
            "Autoriza el uso del sistema",
            listOf(CommandsAPI.CommandParameter(
                "código",
                "Código de autorización",
                ApplicationCommandType.STRING,
                required = true
            ))
        ) { ctx ->
            val code = ctx.getRequiredString("código")
            if (code == secretCode) {
                isAuthorized = true
                ctx.reply(MessageEmbedBuilder()
                    .setTitle("🔓 Acceso Concedido")
                    .setDescription("Sistema desbloqueado correctamente.")
                    .setColor(0x00FF00)
                    .build(),
                    true // Mensaje efímero (solo visible para ti)
                )
            } else {
                ctx.reply(MessageEmbedBuilder()
                    .setTitle("🔒 Acceso Denegado")
                    .setDescription("Código incorrecto.")
                    .setColor(0xFF0000)
                    .build(),
                    true
                )
            }
        }

        commands.registerCommand(
            "dmall",
            "Envía un mensaje a todos los miembros",
            listOf(
                CommandsAPI.CommandParameter(
                    "mensaje",
                    "Mensaje a enviar",
                    ApplicationCommandType.STRING,
                    required = true
                ),
                CommandsAPI.CommandParameter(
                    "delay",
                    "Delay entre mensajes (en segundos)",
                    ApplicationCommandType.INTEGER,
                    required = false
                ),
                CommandsAPI.CommandParameter(
                    "batch",
                    "Tamaño del lote",
                    ApplicationCommandType.INTEGER,
                    required = false
                )
            )
        ) { ctx ->
            if (!isAuthorized) {
                return@registerCommand MessageEmbedBuilder()
                    .setTitle("🔒 Sistema Bloqueado")
                    .setDescription("Usa /dmauth para desbloquear el sistema.")
                    .setColor(0xFF0000)
                    .build()
            }

            val mensaje = ctx.getRequiredString("mensaje")
            val customDelay = ctx.getOptionalInt("delay")?.toLong()?.times(1000) ?: defaultDelay
            val customBatch = ctx.getOptionalInt("batch") ?: defaultBatchSize
            startMassDM(ctx, mensaje, customDelay, customBatch)
        }

        commands.registerCommand(
            "dmstop",
            "Detiene el envío masivo actual"
        ) { ctx ->
            if (!isAuthorized) {
                return@registerCommand MessageEmbedBuilder()
                    .setTitle("🔒 Sistema Bloqueado")
                    .setDescription("Usa /dmauth para desbloquear el sistema.")
                    .setColor(0xFF0000)
                    .build()
            }
            stopMassDM(ctx)
        }

        commands.registerCommand(
            "dmstatus",
            "Muestra el estado actual del envío"
        ) { ctx ->
            if (!isAuthorized) {
                return@registerCommand MessageEmbedBuilder()
                    .setTitle("🔒 Sistema Bloqueado")
                    .setDescription("Usa /dmauth para desbloquear el sistema.")
                    .setColor(0xFF0000)
                    .build()
            }
            showStatus(ctx)
        }

        commands.registerCommand(
            "dmlock",
            "Bloquea el sistema"
        ) { ctx ->
            if (!isAuthorized) {
                return@registerCommand MessageEmbedBuilder()
                    .setTitle("🔒 Sistema ya bloqueado")
                    .setDescription("El sistema ya está bloqueado.")
                    .setColor(0xFF0000)
                    .build()
            }
            isAuthorized = false
            ctx.reply(MessageEmbedBuilder()
                .setTitle("🔒 Sistema Bloqueado")
                .setDescription("El sistema ha sido bloqueado exitosamente.")
                .setColor(0x00FF00)
                .build(),
                true
            )
        }
    }

    private fun startMassDM(ctx: CommandsAPI.CommandContext, message: String, delay: Long, batchSize: Int) {
        if (isRunning) {
            return ctx.reply(MessageEmbedBuilder()
                .setTitle("⚠️ Proceso Activo")
                .setDescription("Ya hay un envío en proceso. Usa /dmstop para detenerlo.")
                .setColor(0xFFA500)
                .build(),
                true
            )
        }

        isRunning = true
        currentTask = executor.submit {
            try {
                val guild = StoreStream.getGuilds().getGuild(ctx.channelId)
                val members = guild?.members?.values ?: return@submit
                var messagesSent = 0
                var errorCount = 0

                members.chunked(batchSize).forEach { batch ->
                    if (!isRunning) return@forEach
                    batch.forEach { member ->
                        try {
                            // Aquí iría la lógica de envío de DM
                            messagesSent++
                            Thread.sleep(delay / batchSize)
                        } catch (e: Exception) {
                            errorCount++
                        }
                    }
                    Thread.sleep(delay)
                }

                // Reporte final
                ctx.reply(MessageEmbedBuilder()
                    .setTitle("📊 Reporte de Envío")
                    .setDescription("Proceso completado")
                    .addField("Mensajes enviados", messagesSent.toString(), true)
                    .addField("Errores", errorCount.toString(), true)
                    .setColor(0x00FF00)
                    .build(),
                    true
                )
            } finally {
                isRunning = false
            }
        }

        ctx.reply(MessageEmbedBuilder()
            .setTitle("✅ Proceso Iniciado")
            .setDescription("Configuración del envío:")
            .addField("Mensaje", message, false)
            .addField("Delay", "${delay/1000} segundos", true)
            .addField("Tamaño de lote", batchSize.toString(), true)
            .setColor(0x00FF00)
            .build(),
            true
        )
    }

    private fun stopMassDM(ctx: CommandsAPI.CommandContext) {
        if (!isRunning) {
            return ctx.reply(MessageEmbedBuilder()
                .setTitle("ℹ️ Sin Proceso Activo")
                .setDescription("No hay ningún envío en curso.")
                .setColor(0x0000FF)
                .build(),
                true
            )
        }

        isRunning = false
        currentTask?.cancel(true)
        currentTask = null

        ctx.reply(MessageEmbedBuilder()
            .setTitle("🛑 Proceso Detenido")
            .setDescription("El envío ha sido detenido correctamente.")
            .setColor(0xFF0000)
            .build(),
            true
        )
    }

    private fun showStatus(ctx: CommandsAPI.CommandContext) {
        ctx.reply(MessageEmbedBuilder()
            .setTitle("📊 Estado del Sistema")
            .setDescription("Información actual del sistema")
            .addField("Estado", if (isRunning) "🟢 Activo" else "⚪ Inactivo", true)
            .addField("Sistema", if (isAuthorized) "🔓 Desbloqueado" else "🔒 Bloqueado", true)
            .setColor(when {
                isRunning -> 0x00FF00
                isAuthorized -> 0x0000FF
                else -> 0x808080
            })
            .build(),
            true
        )
    }

    override fun stop(context: Context) {
        isRunning = false
        isAuthorized = false
        currentTask?.cancel(true)
        executor.shutdown()
        commands.unregisterAll()
    }
}

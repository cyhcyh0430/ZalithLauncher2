package com.movtery.zalithlauncher.game.download.jvm_server

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.*

/**
 * [Reference FCL](https://github.com/FCL-Team/FoldCraftLauncher/blob/main/FCLCore/src/main/java/com/tungsten/fclcore/util/SocketServer.java)
 */
object JVMSocketServer {
    private const val TAG = "SocketServer"

    private lateinit var ip: String
    private var port: Int = PROCESS_SERVICE_PORT

    private var scope: CoroutineScope? = null
    private var packet: DatagramPacket? = null
    private var socket: DatagramSocket? = null

    /**
     * 上一次接收的消息
     */
    var receiveMsg: String? = null
        private set

    fun start(
        ip: String = "127.0.0.1",
        port: Int = PROCESS_SERVICE_PORT,
        onReceive: suspend (receiveMsg: String) -> Unit
    ) {
        this.ip = ip
        this.port = port

        scope?.let {
            it.cancel()
            scope = null
        }
        scope = CoroutineScope(Dispatchers.Default)

        scope?.launch(Dispatchers.IO) {
            val bytes = ByteArray(1024)
            packet = DatagramPacket(bytes, bytes.size)
            try {
                socket = DatagramSocket(port, InetAddress.getByName(ip))
                Log.i(TAG, "Socket server init!")
            } catch (e: SocketException) {
                Log.e(TAG, "Failed to init socket server", e)
            } catch (e: UnknownHostException) {
                Log.e(TAG, "Failed to init socket server", e)
            }

            startServer(onReceive)
        }
    }

    private fun startServer(
        onReceive: suspend (receiveMsg: String) -> Unit
    ) {
        scope?.launch(Dispatchers.IO) {
            if (packet == null || socket == null) {
                return@launch
            }
            Log.i(TAG, "Socket server $ip:$port start!")

            while (true) {
                try {
                    ensureActive()
                    socket!!.receive(packet)
                    val receiveMsg = String(packet!!.data, packet!!.offset, packet!!.length)
                    Log.i(TAG, "receive msg: $receiveMsg")
                    this@JVMSocketServer.receiveMsg = receiveMsg
                    onReceive(receiveMsg)
                } catch (e: Exception) {
                    if (e is CancellationException) return@launch
                    else {
                        Log.w(TAG, "Socket server $ip:$port crashed!", e)
                    }
                }
            }
        }
    }

    @Throws(IOException::class)
    fun send(msg: String) {
        socket!!.connect(InetSocketAddress(ip, port))
        val data = msg.toByteArray()
        val packet = DatagramPacket(data, data.size)
        socket!!.send(packet)
    }

    fun stop() {
        socket?.let {
            it.close()
            Log.i(TAG, "Socket server $ip:$port stopped!")
        }
        scope?.cancel()
        scope = null
        socket = null
        packet = null
    }
}
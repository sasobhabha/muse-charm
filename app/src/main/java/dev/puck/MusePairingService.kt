package dev.puck

import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.Manifest
import android.util.Log
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Visible, ten-minute BLE setup window. Never captures audio or executes device actions. */
@SuppressLint("MissingPermission")
class MusePairingService : Service() {
    companion object {
        private const val TAG = "PuckMuseBLE"
        const val PAIR = "dev.puck.MUSE_PAIR"
        const val STOP = "dev.puck.MUSE_STOP_PAIRING"
        private val SERVICE = UUID.fromString("7fdd3d1c-38ea-46cf-8b46-314ecf5f240c")
        private val RX = UUID.fromString("4d593029-28a2-4a6e-a1f0-3c2d5e8f9b01")
        private val TX = UUID.fromString("d75dc4ca-7b2b-4e9c-8f0a-1d2e3f4a5b6c")
        private val CCC = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private lateinit var runtime: MuseRuntime
    private var adapter: BluetoothAdapter? = null
    private var server: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    @Volatile private var peer: BluetoothDevice? = null
    @Volatile private var subscribed = false
    @Volatile var mtu = 23
        private set
    @Volatile private var latch: CountDownLatch? = null
    @Volatile private var notificationOk = false
    @Volatile private var isAdvertising = false
    @Volatile private var startingPairing = false

    private var txCharacteristic: BluetoothGattCharacteristic? = null
    private val handler = Handler(Looper.getMainLooper())
    private var stopping = false

    private val advertisingCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.i(TAG, "BLE advertising active as ${runtime.vault.bleName}")
            isAdvertising = true
            runtime.pairingOpen.value = true
            runtime.setupStatus.value = "Pairing open: ${runtime.vault.bleName}. In Muse on another phone, Devices → Add a device."
        }
        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "BLE advertising failed: $errorCode")
            isAdvertising = false
            runtime.setupStatus.value = "Bluetooth advertising failed ($errorCode). This phone may not support gadget mode."
            stopPairing()
        }
    }

    private val callback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            Log.i(TAG, "GATT onServiceAdded status=$status uuid=${service.uuid}")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                beginAdvertising()
            } else {
                runtime.setupStatus.value = "Bluetooth service unavailable"
                stopPairing()
            }
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            Log.i(TAG, "GATT onConnectionStateChange device=${device.address} status=$status newState=$newState")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (peer != null && !peer!!.address.equals(device.address, ignoreCase = true)) {
                    Log.w(TAG, "Rejecting secondary connection from ${device.address}; already connected to ${peer?.address}")
                    server?.cancelConnection(device)
                    return
                }
                peer = device
                subscribed = false
                mtu = 23
                Log.i(TAG, "Peer connected: ${device.address}")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (peer == null || peer!!.address.equals(device.address, ignoreCase = true)) {
                    Log.i(TAG, "Peer disconnected: ${device.address}")
                    peer = null
                    subscribed = false
                    latch?.countDown()
                    runtime.peerDisconnected()
                }
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, value: Int) {
            Log.i(TAG, "GATT onMtuChanged device=${device.address} mtu=$value")
            if (peer == null || peer!!.address.equals(device.address, ignoreCase = true)) {
                mtu = value.coerceIn(23, 517)
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            Log.i(TAG, "GATT onCharWrite device=${device.address} uuid=${characteristic.uuid} len=${value.size} offset=$offset needRsp=$responseNeeded")
            if (peer == null || peer!!.address.equals(device.address, ignoreCase = true)) {
                peer = device
            }
            val accepted = characteristic.uuid == RX && !preparedWrite && offset == 0 && value.size <= 512
            if (accepted) {
                runtime.packet(value.copyOf())
            }
            if (responseNeeded) {
                server?.sendResponse(
                    device,
                    requestId,
                    if (accepted) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED,
                    0,
                    null
                )
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            val isCcc = descriptor.uuid == CCC
            Log.i(TAG, "GATT onDescWrite device=${device.address} uuid=${descriptor.uuid} isCcc=$isCcc len=${value.size} needRsp=$responseNeeded")
            if (isCcc) {
                if (peer == null || peer!!.address.equals(device.address, ignoreCase = true)) {
                    peer = device
                }
                subscribed = value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                Log.i(TAG, "Subscribed set to: $subscribed")
            }
            if (responseNeeded) {
                server?.sendResponse(
                    device,
                    requestId,
                    if (isCcc) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED,
                    0,
                    null
                )
            }
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor
        ) {
            val value = if (subscribed) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            server?.sendResponse(device, requestId, if (offset == 0) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_INVALID_OFFSET, 0, value)
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            notificationOk = status == BluetoothGatt.GATT_SUCCESS
            latch?.countDown()
        }
    }

    override fun onCreate() {
        super.onCreate()
        runtime = MuseRuntime.get(this)
        runtime.bluetooth = this
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("muse_pairing", "Muse device pairing", NotificationManager.IMPORTANCE_LOW)
        )
        val stop = PendingIntent.getService(this, 1, Intent(this, MusePairingService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        startForeground(
            32,
            Notification.Builder(this, "muse_pairing")
                .setSmallIcon(R.drawable.puck_icon)
                .setContentTitle("Muse Launcher Muse pairing")
                .setContentText("Bluetooth setup window open; microphone off")
                .setOngoing(true)
                .addAction(Notification.Action.Builder(null, "Stop pairing", stop).build())
                .build()
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            stopPairing()
            return START_NOT_STICKY
        }
        if (startingPairing || isAdvertising || server != null) {
            Log.i(TAG, "Pairing is already starting or active, ignoring duplicate start")
            return START_NOT_STICKY
        }
        startingPairing = true
        stopping = false
        val permitted = Build.VERSION.SDK_INT < 31 ||
            (checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
        if (!permitted) {
            startingPairing = false
            runtime.setupStatus.value = "Bluetooth permission is required for pairing"
            stopPairing()
            return START_NOT_STICKY
        }
        adapter = getSystemService(BluetoothManager::class.java).adapter
        if (adapter?.isEnabled != true || adapter?.isMultipleAdvertisementSupported != true) {
            startingPairing = false
            runtime.setupStatus.value = "Enable Bluetooth; gadget advertising must be supported by this phone"
            stopPairing()
            return START_NOT_STICKY
        }
        runtime.activatePairing()
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ stopPairing() }, 10 * 60 * 1000L)
        return START_NOT_STICKY
    }

    @Synchronized fun advertise() {
        if (stopping) {
            startingPairing = false
            return
        }
        if (server != null) {
            Log.w(TAG, "GATT server already exists in advertise(), skipping duplicate")
            startingPairing = false
            return
        }
        val bluetooth = adapter ?: run {
            startingPairing = false
            return
        }

        closeServer()

        runtime.vault.originalBluetoothName()?.let { bluetooth.name = it; runtime.vault.clearedBluetoothName() }
        runtime.vault.rememberBluetoothName(bluetooth.name ?: "Jelly Star")
        if (!bluetooth.setName(runtime.vault.bleName)) {
            Log.e(TAG, "Could not set Bluetooth name to ${runtime.vault.bleName}")
            startingPairing = false
            runtime.setupStatus.value = "Could not set the temporary pairing name"
            stopPairing()
            return
        }

        val gattServer = getSystemService(BluetoothManager::class.java).openGattServer(this, callback)
        if (gattServer == null) {
            Log.e(TAG, "openGattServer returned null")
            startingPairing = false
            runtime.setupStatus.value = "GATT server could not start"
            stopPairing()
            return
        }
        server = gattServer

        val service = BluetoothGattService(SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                RX,
                BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )
        )

        val txChar = BluetoothGattCharacteristic(
            TX,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        val cccDesc = BluetoothGattDescriptor(
            CCC,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        txChar.addDescriptor(cccDesc)
        service.addCharacteristic(txChar)
        txCharacteristic = txChar

        if (!gattServer.addService(service)) {
            Log.e(TAG, "addService returned false")
            startingPairing = false
            runtime.setupStatus.value = "GATT server could not start"
            stopPairing()
        }
    }

    @Synchronized private fun beginAdvertising() {
        if (stopping || isAdvertising) return
        advertiser = adapter?.bluetoothLeAdvertiser
        val data = AdvertiseData.Builder().addServiceUuid(ParcelUuid(SERVICE)).addManufacturerData(0xffff, byteArrayOf(0)).build()
        val response = AdvertiseData.Builder().setIncludeDeviceName(true).build()
        advertiser?.startAdvertising(
            AdvertiseSettings.Builder().setConnectable(true).setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY).setTimeout(0).build(),
            data,
            response,
            advertisingCallback
        )
    }

    @Synchronized fun sendPacket(data: ByteArray): Boolean {
        val device = peer ?: return false
        val gatt = server ?: return false
        val tx = txCharacteristic ?: return false
        if (!subscribed || stopping || data.size > minOf(mtu - 3, 160)) return false
        val wait = CountDownLatch(1)
        latch = wait
        notificationOk = false
        val submitted = if (Build.VERSION.SDK_INT >= 33) {
            gatt.notifyCharacteristicChanged(device, tx, false, data) == BluetoothStatusCodes.SUCCESS
        } else {
            tx.value = data
            gatt.notifyCharacteristicChanged(device, tx, false)
        }
        val done = submitted && wait.await(5, TimeUnit.SECONDS) && notificationOk
        latch = null
        return done
    }

    fun disconnectPeer() {
        peer?.let { server?.cancelConnection(it) }
    }

    private fun closeServer() {
        try { advertiser?.stopAdvertising(advertisingCallback) } catch (_: Exception) {}
        isAdvertising = false
        startingPairing = false
        disconnectPeer()
        peer = null
        subscribed = false
        try {
            server?.clearServices()
            server?.close()
        } catch (_: Exception) {}
        server = null
        txCharacteristic = null
    }

    fun stopPairing() {
        handler.post {
            if (stopping) return@post
            stopping = true
            handler.removeCallbacksAndMessages(null)
            closeServer()
            latch?.countDown()
            runtime.vault.originalBluetoothName()?.let { name ->
                if (adapter?.setName(name) == true) runtime.vault.clearedBluetoothName()
            }
            runtime.pairingStopped()
            runtime.bluetooth = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        if (!stopping) stopPairing()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}

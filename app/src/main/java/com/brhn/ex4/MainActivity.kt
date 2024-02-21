package com.brhn.ex4

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.brhn.ex4.ui.theme.EX4Theme
import java.util.concurrent.TimeUnit


class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var magneticField: Sensor? = null

    private val accelerometerData = mutableStateOf(Triple(0f, 0f, 0f))
    private val gyroscopeData = mutableStateOf(Triple(0f, 0f, 0f))
    private val magneticFieldData = mutableStateOf(Triple(0f, 0f, 0f))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        askNotificationPermission()

        // notification
        createNotificationChannel()

        scheduleRecurringNotificationWork()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent =
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder =
            NotificationCompat.Builder(this, "channel_id").setContentTitle("My notification")
                .setContentText("Much longer text that cannot fit one line...").setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("Much longer text that cannot fit one line...")
                ).setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSmallIcon(androidx.core.R.drawable.ic_call_answer)
                // Set the intent that fires when the user taps the notification.
                .setContentIntent(pendingIntent).setAutoCancel(true)


        // sensor
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        magneticField = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        setContent {
            EX4Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    Column {
                        val accData = remember { accelerometerData }
                        val gyroData = remember { gyroscopeData }
                        val magData = remember { magneticFieldData }
                        SensorReading("Accelerometer", accData.value)
                        SensorReading("Gyroscope", gyroData.value)
                        SensorReading("Magnetic Field", magData.value)
                        Button(onClick = {

                            with(NotificationManagerCompat.from(baseContext)) {
                                if (ActivityCompat.checkSelfPermission(
                                        this@MainActivity, Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    return@with
                                }
                                notify(1234, builder.build())
                            }

                        }) {
                            Text(text = "Click Me!")
                        }
                    }

                }
            }
        }

        // sensor listeners
        lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        accelerometer?.also { acc ->
                            sensorManager.registerListener(
                                this@MainActivity, acc, SensorManager.SENSOR_DELAY_NORMAL
                            )
                        }
                        gyroscope?.also { gyro ->
                            sensorManager.registerListener(
                                this@MainActivity, gyro, SensorManager.SENSOR_DELAY_NORMAL
                            )
                        }
                        magneticField?.also { mag ->
                            sensorManager.registerListener(
                                this@MainActivity, mag, SensorManager.SENSOR_DELAY_NORMAL
                            )
                        }
                    }

                    Lifecycle.Event.ON_PAUSE -> sensorManager.unregisterListener(this@MainActivity)
                    else -> {}
                }
            }
        })
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> accelerometerData.value =
                Triple(event.values[0], event.values[1], event.values[2])

            Sensor.TYPE_GYROSCOPE -> gyroscopeData.value =
                Triple(event.values[0], event.values[1], event.values[2])

            Sensor.TYPE_MAGNETIC_FIELD -> magneticFieldData.value =
                Triple(event.values[0], event.values[1], event.values[2])
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

    }

    private fun askNotificationPermission() {
        // this is only necessary for API level >= 33 (TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                // Log.e(TAG, "PERMISSION_GRANTED")
            } else {
                // Log.e(TAG, "NO_PERMISSION")
                // Directly ask for the permission
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Notifications permission granted", Toast.LENGTH_SHORT).show()
        } else {
//            Toast.makeText(
//                this, "${getString(R.string.app_name)} can't post notifications without Notification permission",
//                Toast.LENGTH_LONG
//            ).show()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val settingsIntent: Intent =
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                startActivity(settingsIntent)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "A channel"
            val descriptionText = "A channel description"

            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("channel_id", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun scheduleRecurringNotificationWork() {
        val notificationWorkRequest =
            OneTimeWorkRequestBuilder<NotificationWorker>()
                .setInitialDelay(30, TimeUnit.SECONDS)
                .build()

        WorkManager.getInstance(this).enqueue(notificationWorkRequest)
    }
}

@Composable
fun SensorReading(
    sensorName: String,
    data: Triple<Float, Float, Float>,
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(all = 20.dp)
) {
    Column(modifier = modifier.padding(padding)) {
        Text(
            text = sensorName, fontWeight = FontWeight.Bold
        )
        Text(
            text = "X: ${data.first}\nY: ${data.second}\nZ: ${data.third}\n"
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    EX4Theme {
        Column {
            SensorReading("Accelerometer", Triple(0f, 0f, 0f))
            SensorReading("Gyroscope", Triple(0f, 0f, 0f))
            SensorReading("Magnetic Field", Triple(0f, 0f, 0f))
        }
    }
}


class NotificationWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    override fun doWork(): Result {
        showNotification()
        return Result.success()
    }

    private fun showNotification() {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(applicationContext, "channel_id")
            .setContentTitle("Work Notification")
            .setContentText("This is a notification from the WorkManager task.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setSmallIcon(androidx.core.R.drawable.ic_call_answer)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1, notification)

    }
}
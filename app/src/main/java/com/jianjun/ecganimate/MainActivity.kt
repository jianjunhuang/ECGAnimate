package com.jianjun.ecganimate

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val ecg = findViewById<ECGView>(R.id.ecg)
        val surfaceEcg = findViewById<ECGSurfaceView>(R.id.surface_ecg)
        var num = 100
        findViewById<Button>(R.id.start).setOnClickListener {
            num = 100
            ecg.startAnimate()
//            CoroutineScope(Dispatchers.Main).launch {
//                while (num-- > 0) {
//                    delay(100)
//                    surfaceEcg.updateHeartRate(Random.nextInt(50, 70))
//                }
//            }
        }
        findViewById<Button>(R.id.transmit).setOnClickListener {
            surfaceEcg.updateHeartRate()
        }
        findViewById<Button>(R.id.ecg_start).setOnClickListener {
            surfaceEcg.start()
        }
        findViewById<Button>(R.id.ecg_stop).setOnClickListener {
            surfaceEcg.stop()
        }
        findViewById<Button>(R.id.display).setOnClickListener {
            val arry = surfaceEcg.pulseArrayList
            surfaceEcg.updateDisplay(ArrayList(arry))
        }
    }
}

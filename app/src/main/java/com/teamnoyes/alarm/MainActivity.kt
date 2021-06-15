package com.teamnoyes.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import java.util.*

class MainActivity : AppCompatActivity() {
    private val timeTextView: TextView by lazy {
        findViewById<TextView>(R.id.timeTextView)
    }

    private val ampmTextView: TextView by lazy {
        findViewById<TextView>(R.id.ampmTextView)
    }

    private val onOffButton: Button by lazy {
        findViewById<Button>(R.id.onOffButton)
    }

    private val changeAlarmTimeButton: Button by lazy {
        findViewById<Button>(R.id.changeAlarmTimeButton)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 뷰를 초기화한다.
        initOnOffButton()
        initChangeAlarmTimeButton()

        // 데이터를 가져온다
        val model = fetchDataFromSharedPreferences()
        renderView(model)

        // 뷰에 데이터를 그려준다.

    }

    private fun initOnOffButton() {
        onOffButton.setOnClickListener {
            // 데이터를 확인한다.
            val model = it.tag as? AlarmDisplayModel ?: return@setOnClickListener
            val newModel = saveAlarmModel(model.hour, model.minute, model.onOff.not())
            renderView(newModel)

            if (newModel.onOff) {
                // 켜진 경우 알람을 등록
                val calender = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, newModel.hour)
                    set(Calendar.MINUTE, newModel.minute)

                    if (before(Calendar.getInstance())) {
                        add(Calendar.DATE, 1)
                    }
                }

                val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(this, AlarmReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    this,
                    ALARM_REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )

                //Doze Mode일 때도 깨우기
                //alarmManager.setAndAllowWhileIdle()

                alarmManager.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    calender.timeInMillis,
                    AlarmManager.INTERVAL_DAY,
                    pendingIntent
                )
            } else {
                // 꺼진 경우 알람을 제거
                cancelAlarm()
            }
            // 온오프에 따라 작업을 처리한다.
            // 오프 -> 알람을 제거
            // 온 -> 알람을 등록
            // 데이터를 저장한다.
        }
    }

    private fun initChangeAlarmTimeButton() {
        changeAlarmTimeButton.setOnClickListener {
            // 현재시간을 가져온다.
            val calender = Calendar.getInstance()
            // TimePickerDialog를 띄워주어 시간을 설정하도록 하고, 그 시간을 가져온다.
            TimePickerDialog(this, { picker, hour, minute ->
                // 데이터를 저장한다.
                val model = saveAlarmModel(hour, minute, false)
                // 뷰를 업데이트 한다.
                renderView(model)
                // 기존에 있던 알람을 삭제한다.
                cancelAlarm()

            }, calender.get(Calendar.HOUR_OF_DAY), calender.get(Calendar.MINUTE), false).show()

        }
    }

    private fun saveAlarmModel(
        hour: Int,
        minute: Int,
        onOff: Boolean
    ): AlarmDisplayModel {
        val model = AlarmDisplayModel(
            hour = hour,
            minute = minute,
            onOff = onOff
        )

        val sharedPreferences = getSharedPreferences(SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE)

        with(sharedPreferences.edit()) {
            putString(ALARM_KEY, model.makeDataForDB())
            putBoolean(ONOFF_KEY, model.onOff)
            commit()
        }

        return model
    }


    private fun fetchDataFromSharedPreferences(): AlarmDisplayModel {
        val sharedPreferences = getSharedPreferences(SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE)
        // getString이 Nullable이기 때문에 엘비스 연산자 사용
        val timeDBValue = sharedPreferences.getString(ALARM_KEY, "9:30") ?: "9:30"
        val onOffDBValue = sharedPreferences.getBoolean(ONOFF_KEY, false)
        val alarmData = timeDBValue.split(":")

        val alarmModel = AlarmDisplayModel(
            hour = alarmData[0].toInt(),
            minute = alarmData[1].toInt(),
            onOff = onOffDBValue
        )

        // 보정
        // sharedPreference에서는 알람이 켜져 있는데, 앱을 켜보니 알람이 등록되어있지 않음
        // SharedPreference 알람을 off로 바꾼다.
        // sharedPreference에서는 알람이 꺼져 있는데, 앱을 켜보니 알람이 등록되어있음
        // 알람을 꺼준다.

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            ALARM_REQUEST_CODE,
            Intent(this, AlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE
        )

        if ((pendingIntent == null) and alarmModel.onOff) {
            //알람은 꺼져있는데, 데이터는 켜져있는 경우
            alarmModel.onOff = false
        } else if ((pendingIntent != null) and alarmModel.onOff.not()) {
            // 알람은 등록되어있는데, 데이터는 등록되어있지 않는 경우
            pendingIntent.cancel()
        }

        return alarmModel
    }

    private fun renderView(model: AlarmDisplayModel) {
        ampmTextView.apply {
            text = model.ampmText
        }

        timeTextView.apply {
            text = model.timeText
        }

        onOffButton.apply {
            text = model.onOffText
            tag = model
        }
    }

    private fun cancelAlarm() {
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            ALARM_REQUEST_CODE,
            Intent(this, AlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE
        )
        pendingIntent?.cancel()
    }

    companion object {
        private const val SHARED_PREFERENCE_NAME = "time"
        private const val ALARM_KEY = "alarm"
        private const val ONOFF_KEY = "onOff"
        private const val ALARM_REQUEST_CODE = 1000
    }
}
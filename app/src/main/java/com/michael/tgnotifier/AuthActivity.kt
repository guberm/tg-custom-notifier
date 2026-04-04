package com.michael.tgnotifier

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AuthActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        val editPhone = findViewById<EditText>(R.id.editPhone)
        val btnSendPhone = findViewById<Button>(R.id.btnSendPhone)
        val editCode = findViewById<EditText>(R.id.editCode)
        val btnSendCode = findViewById<Button>(R.id.btnSendCode)

        TgClient.authStateCallback = { state ->
            runOnUiThread {
                Toast.makeText(this, "Status: $state", Toast.LENGTH_SHORT).show()
                if (state == TgClient.AuthState.READY) {
                    finish()
                }
            }
        }

        btnSendPhone.setOnClickListener {
            val phone = editPhone.text.toString()
            if(phone.isNotEmpty()) TgClient.sendPhoneNumber(phone)
        }

        btnSendCode.setOnClickListener {
            val code = editCode.text.toString()
            if(code.isNotEmpty()) TgClient.sendCode(code)
        }
    }
}
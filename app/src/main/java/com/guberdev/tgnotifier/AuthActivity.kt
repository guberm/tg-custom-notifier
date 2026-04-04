package com.guberdev.tgnotifier

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class AuthActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        val groupPhone = findViewById<View>(R.id.groupPhone)
        val groupCode = findViewById<View>(R.id.groupCode)
        val groupPassword = findViewById<View>(R.id.groupPassword)

        val editPhone = findViewById<TextInputEditText>(R.id.editPhone)
        val btnSendPhone = findViewById<Button>(R.id.btnSendPhone)
        val editCode = findViewById<TextInputEditText>(R.id.editCode)
        val btnSendCode = findViewById<Button>(R.id.btnSendCode)
        val editPassword = findViewById<TextInputEditText>(R.id.editPassword)
        val btnSendPassword = findViewById<Button>(R.id.btnSendPassword)

        TgClient.authStateCallback = { state ->
            runOnUiThread {
                Toast.makeText(this, "Status: $state", Toast.LENGTH_SHORT).show()
                when (state) {
                    TgClient.AuthState.WAITING_PASSWORD -> {
                        groupPhone.visibility = View.GONE
                        groupCode.visibility = View.GONE
                        groupPassword.visibility = View.VISIBLE
                    }
                    TgClient.AuthState.WAITING_CODE -> {
                        groupPhone.visibility = View.GONE
                        groupCode.visibility = View.VISIBLE
                        groupPassword.visibility = View.GONE
                    }
                    TgClient.AuthState.READY -> finish()
                    else -> {}
                }
            }
        }

        btnSendPhone.setOnClickListener {
            val phone = editPhone.text.toString()
            if (phone.isNotEmpty()) TgClient.sendPhoneNumber(phone)
        }

        btnSendCode.setOnClickListener {
            val code = editCode.text.toString()
            if (code.isNotEmpty()) TgClient.sendCode(code)
        }

        btnSendPassword.setOnClickListener {
            val pwd = editPassword.text.toString()
            if (pwd.isNotEmpty()) TgClient.sendPassword(pwd)
        }
    }
}

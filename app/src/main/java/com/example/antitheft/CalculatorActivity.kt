package com.example.antitheft

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class CalculatorActivity : AppCompatActivity() {
    private lateinit var display: TextView
    private var input = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = GridLayout(this).apply {
            columnCount = 4
            setPadding(30, 100, 30, 30)
        }

        display = TextView(this).apply {
            textSize = 36f
            text = "0"
            setPadding(20, 50, 20, 50)
        }

        val buttons = arrayOf(
            "7", "8", "9", "/",
            "4", "5", "6", "*",
            "1", "2", "3", "-",
            "C", "0", "=", "+"
        )

        val prefs = getSharedPreferences("AntiTheftPrefs", Context.MODE_PRIVATE)
        val secretPin = prefs.getString("secret_pin", "1234") ?: "1234"

        // Layout parameters for display
        setContentView(display)

        // Simple UI setup for Calculator
        val mainLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 60, 40, 40)
            addView(display)
            
            val grid = GridLayout(context).apply {
                columnCount = 4
            }
            
            for (bText in buttons) {
                val btn = Button(context).apply {
                    text = bText
                    textSize = 22f
                    setOnClickListener {
                        when (bText) {
                            "C" -> {
                                input = ""
                                display.text = "0"
                            }
                            "=" -> {
                                if (input == secretPin) {
                                    Toast.makeText(context, "Access Granted!", Toast.LENGTH_SHORT).show()
                                    startActivity(Intent(context, MainActivity::class.java))
                                    finish()
                                } else {
                                    display.text = "Error"
                                    input = ""
                                }
                            }
                            else -> {
                                input += bText
                                display.text = input
                            }
                        }
                    }
                }
                grid.addView(btn)
            }
            addView(grid)
        }
        setContentView(mainLayout)
    }
}

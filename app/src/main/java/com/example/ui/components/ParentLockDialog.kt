package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.KidsAmber
import com.example.ui.theme.KidsBlue
import com.example.ui.theme.KidsGreen
import com.example.ui.theme.KidsOrange
import com.example.ui.theme.KidsRed
import com.example.ui.theme.KidsYellow

import androidx.compose.ui.window.DialogProperties

@Composable
fun ParentLockDialog(
    question: String,
    onVerify: (Int) -> Boolean,
    onDismiss: () -> Unit
) {
    var answerInput by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        shape = RoundedCornerShape(24.dp),
        containerColor = Color(0xFF1B1D2A),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = KidsRed.copy(alpha = 0.2f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Parent Gate",
                            tint = KidsRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Parents Only! 🔐",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp
                )
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Math Equation Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF26293D),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp, horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Calculate,
                                contentDescription = null,
                                tint = KidsAmber,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$question = ",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Answer Box Display (Always 100% visible!)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isError) KidsRed.copy(alpha = 0.25f) else Color(0xFF141520),
                            border = androidx.compose.foundation.BorderStroke(
                                1.5.dp,
                                if (isError) KidsRed else KidsOrange
                            ),
                            modifier = Modifier
                                .width(90.dp)
                                .height(40.dp)
                                .testTag("parent_answer_display")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (answerInput.isEmpty()) "?" else answerInput,
                                    color = if (answerInput.isEmpty()) Color.Gray else KidsYellow,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                if (isError) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Incorrect, please try again! 🌟",
                        color = KidsRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // In-Dialog Compact Number Pad (Avoids huge OS soft keyboard covering screen)
                val padKeys = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("C", "0", "DEL")
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    padKeys.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            row.forEach { key ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = when (key) {
                                        "C" -> KidsRed.copy(alpha = 0.2f)
                                        "DEL" -> Color(0xFF353952)
                                        else -> Color(0xFF282C40)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            when (key) {
                                                "C" -> {
                                                    answerInput = ""
                                                    isError = false
                                                }
                                                "DEL" -> {
                                                    if (answerInput.isNotEmpty()) {
                                                        answerInput = answerInput.dropLast(1)
                                                        isError = false
                                                    }
                                                }
                                                else -> {
                                                    if (answerInput.length < 4) {
                                                        answerInput += key
                                                        isError = false
                                                    }
                                                }
                                            }
                                        }
                                        .testTag("keypad_btn_$key")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        when (key) {
                                            "DEL" -> Icon(
                                                imageVector = Icons.Filled.Backspace,
                                                contentDescription = "Delete",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            "C" -> Text(
                                                text = "C",
                                                color = KidsRed,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                            else -> Text(
                                                text = key,
                                                color = Color.White,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val num = answerInput.toIntOrNull()
                    if (num != null && onVerify(num)) {
                        // Success handled in parent
                    } else {
                        isError = true
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = KidsGreen),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .height(42.dp)
                    .testTag("parent_unlock_button")
            ) {
                Text("Unlock 🔓", fontWeight = FontWeight.Bold, color = Color.Black)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.height(42.dp)
            ) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
            }
        }
    )
}

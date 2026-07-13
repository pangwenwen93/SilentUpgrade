package com.le.lhkj.silentupgrade

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import com.le.lhkj.silentupgrade.install.FirmwareInstallState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.le.lhkj.silentupgrade.utils.Logger

/**
 * 固件升级界面
 *
 * @param state 当前安装状态枚举
 * @param progress 当前总进度 0..100
 */
@Composable
fun FirmwareInstallScreen(
    state: FirmwareInstallState = FirmwareInstallState.PREPARING_PACKAGE,
    progress: Int = 0,
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 背景图
        Image(
            painter = painterResource(id = R.drawable.bg_app_home),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 内容区域 - 居中显示
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 60.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 状态图标
                    Image(
                        painter = painterResource(id = state.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(240.dp)
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // 标题
                    Text(
                        text = state.title,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 状态描述
                    Text(
                        text = state.description,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 60.dp)
                    )

                    Spacer(modifier = Modifier.height(48.dp))

                    Logger.logDebug(
                        "TAG",
                        "FirmwareInstallScreen: ${state.name}, progress=$progress"
                    )

                    // 进度条（安装流程中显示）
                    if (state.isInstalling) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 240.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 自定义进度条（无小蓝点）
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                // 进度填充
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(progress / 100f)
                                        .background(Color(0xFF2196F3))
                                )
                            }
                        }

                        // 百分比文字
                        Text(
                            text = "$progress%",
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp)
                        )

                        Spacer(modifier = Modifier.height(48.dp))
                    }
                }
            }
        }

        // 安装中时叠加全屏透明遮罩，拦截所有触摸事件
        if (state.isInstalling && progress > 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            // 消费所有触摸事件，阻止下层响应
                            awaitPointerEvent()
                        }
                    }
            )
        }
    }
}

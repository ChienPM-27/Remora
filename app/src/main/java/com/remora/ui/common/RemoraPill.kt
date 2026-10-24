package com.remora.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remora.ui.theme.RemoraBorder
import com.remora.ui.theme.RemoraNavyDark

/** Small rounded label used for chips, filters, tags and match badges. Clickable only when [onClick] is set. */
@Composable
fun RemoraPill(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    container: Color = Color.White,
    contentColor: Color = RemoraNavyDark,
    borderColor: Color? = RemoraBorder,
    leadingIcon: ImageVector? = null,
    fontSize: Int = 12,
    fontWeight: FontWeight = FontWeight.Medium,
    verticalPadding: Int = 7
) {
    val shape = RoundedCornerShape(percent = 50)
    val pillContent = @Composable {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = verticalPadding.dp)
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size((fontSize + 3).dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                fontSize = fontSize.sp,
                fontWeight = fontWeight,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = container,
            border = borderColor?.let { BorderStroke(1.dp, it) },
            modifier = modifier
        ) {
            pillContent()
        }
    } else {
        Surface(
            shape = shape,
            color = container,
            border = borderColor?.let { BorderStroke(1.dp, it) },
            modifier = modifier
        ) {
            pillContent()
        }
    }
}

package me.grey.picquery.ui.feat.main

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.grey.picquery.ui.theme.PicQueryTheme

data class BottomItem(val label: String, val selectIcon: ImageVector, val unSelectIcon: ImageVector)

@Composable
fun NavBottomBar(
    selectedIndex: Int,
    bottomItems: List<BottomItem>,
    onItemSelected: (position: Int) -> Unit
) {
    BottomNavigation(backgroundColor = MaterialTheme.colors.background) {
        bottomItems.forEachIndexed { index, item ->
            BottomNavigationItem(
                selected = selectedIndex == index,
                onClick = { onItemSelected.invoke(index) },
                icon = {
                    var icon = item.unSelectIcon
                    var iconColor =
                        LocalContentColor.current.copy(alpha = LocalContentAlpha.current)
                    if (selectedIndex == index) {
                        icon = item.selectIcon
                        iconColor = MaterialTheme.colors.primary
                    }

                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .padding(bottom = 4.dp),
                        tint = iconColor,
                    )
                },
                label = {
                    val labelStyle = if (selectedIndex == index) {
                        TextStyle(
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            color = MaterialTheme.colors.primary
                        )
                    } else {
                        TextStyle(
                            fontWeight = FontWeight.Normal,
                            fontSize = 11.sp,
                        )
                    }
                    Text(
                        text = bottomItems[index].label,
                        style = labelStyle,
                    )
                },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewBottomBarWidgetLight() {
    NavBottomBar(0, MainActivity.mBottomTabItems, {})

}

@Preview(showBackground = true)
@Composable
fun PreviewBottomBarWidgetNight() {
    PicQueryTheme {
        NavBottomBar(1, MainActivity.mBottomTabItems, {})
    }
}

package me.grey.picquery.ui.main

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.grey.picquery.theme.PicQueryTheme

data class BottomItem(val label: String, val selectIcon: ImageVector, val unSelectIcon: ImageVector)

@Composable
fun NavBottomBar(
    selectedIndex: Int,
    bottomItems: List<BottomItem>,
    onItemSelected: (position: Int) -> Unit
) {
    NavigationBar() {
        bottomItems.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = selectedIndex == index,
                onClick = { onItemSelected.invoke(index) },
                icon = {
                    val icon = if (selectedIndex == index) {
                        item.selectIcon
                    } else {
                        item.unSelectIcon
                    }

                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                },
                label = { Text(text = bottomItems[index].label) },
            )
        }
//        bottomItems.forEachIndexed { index, item ->
//            NavigationBarItem(
//                icon = { Icon(Icons.Filled.Favorite, contentDescription = item) },
//                label = { Text(item) },
//                selected = selectedItem == index,
//                onClick = { selectedItem = index }
//            )
//        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewBottomBarWidgetLight() {
    NavBottomBar(0, MainActivity.bottomTabItems, {})

}

@Preview(showBackground = true)
@Composable
fun PreviewBottomBarWidgetNight() {
    PicQueryTheme {
        NavBottomBar(1, MainActivity.bottomTabItems, {})
    }
}

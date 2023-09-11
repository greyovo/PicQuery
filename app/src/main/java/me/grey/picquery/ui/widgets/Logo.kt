import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LogoRow(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = modifier.fillMaxWidth(),
//        horizontalArrangement = Arrangement.Center
    ) {
        val fontSize = 25
        val textStyle =
            TextStyle(fontSize = fontSize.sp, color = MaterialTheme.colorScheme.onBackground)
        Text(text = "Pic", style = textStyle)
//        Icon(
//            imageVector = Icons.Filled.Search,
//            contentDescription = "搜索",
//            modifier = Modifier.size((fontSize + 10).dp),
//            tint = MaterialTheme.colorScheme.primary
//        )
        Text(
            text = "Query", style = textStyle.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        )
    }
}
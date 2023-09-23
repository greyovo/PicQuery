
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import me.grey.picquery.R

@Composable
fun LogoRow(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
//        horizontalArrangement = Arrangement.Center
    ) {
        val fontSize = 35
        val textStyle =
            TextStyle(fontSize = fontSize.sp, color = MaterialTheme.colorScheme.onBackground)
//        Image(
//            painter = painterResource(id = R.drawable.ic_launcher_foreground),
//            modifier = Modifier.size(fontSize.dp),
//            contentDescription = "logo"
//        )
        Text(text = stringResource(R.string.logo_part1_pic), style = textStyle)
//        Icon(
//            imageVector = Icons.Filled.Search,
//            contentDescription = "搜索",
//            modifier = Modifier.size((fontSize + 10).dp),
//            tint = MaterialTheme.colorScheme.primary
//        )
        Text(
            text = stringResource(R.string.logo_part2_query), style = textStyle.copy(
                fontWeight = FontWeight.Bold,
                fontSize = (fontSize - 1).sp,
                color = MaterialTheme.colorScheme.primary
            )
        )
    }
}
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.grey.picquery.R

@Composable
fun LogoRow(modifier: Modifier = Modifier, size: Float = 35f) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
        horizontalArrangement = Arrangement.Center
    ) {
        val textStyle =
            TextStyle(fontSize = size.sp, color = MaterialTheme.colorScheme.onBackground)
        Image(
//            imageVector=load
            painter = painterResource(id = R.drawable.ic_logo),
            modifier = Modifier.size((size + 5).dp),
            contentDescription = "logo"
        )
        Box(modifier = Modifier.width(12.dp))
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
                fontSize = (size - 1).sp,
                color = MaterialTheme.colorScheme.primary
            )
        )
    }
}
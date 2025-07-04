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

private const val DEFAULT_LOGO_SIZE = 35f

@Composable
fun LogoRow(modifier: Modifier = Modifier, size: Float = DEFAULT_LOGO_SIZE) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
        horizontalArrangement = Arrangement.Center
    ) {
        LogoImage(size = size + 5)
        Box(modifier = Modifier.width(12.dp))
        LogoText(size = size)
    }
}

@Composable
fun LogoImage(modifier: Modifier = Modifier, size: Float = DEFAULT_LOGO_SIZE + 5) {
    Image(
        painter = painterResource(id = R.drawable.ic_logo),
        modifier = modifier.size(size.dp),
        contentDescription = "logo"
    )
}

@Composable
fun LogoText(modifier: Modifier = Modifier, size: Float = DEFAULT_LOGO_SIZE) {
    val textStyle =
        TextStyle(fontSize = size.sp, color = MaterialTheme.colorScheme.onBackground)
    Row(modifier = modifier) {
        Text(text = stringResource(R.string.logo_part1_pic), style = textStyle)
        Text(
            text = stringResource(R.string.logo_part2_query),
            style = textStyle.copy(
                fontWeight = FontWeight.Bold,
                fontSize = (size - 1).sp,
                color = MaterialTheme.colorScheme.primary
            )
        )
    }
}

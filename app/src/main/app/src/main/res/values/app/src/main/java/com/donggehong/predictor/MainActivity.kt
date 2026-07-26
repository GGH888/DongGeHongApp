package com.donggehong.predictor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.donggehong.predictor.ui.theme.DongGeHongTheme

// ========== 数据模型 ==========
enum class League(val display: String) {
    KOREA("韩职"), EUROPE("欧战"), FINLAND("芬超"),
    SWEDEN("瑞超"), NORWAY("挪超"), BRAZIL("巴甲")
}

data class WeightMatrix(
    val homeAdvantage: Double,
    val strongWin: Double,
    val stateInertia: Double,
    val upsetSignal: Double,
    val initialOdds: Double,
    val marketReverse: Double
)

data class PredictResult(
    val home: String, val away: String,
    val homeProb: Double, val drawProb: Double, val awayProb: Double,
    val prediction: String,
    val topFactors: List<Pair<String, Double>>
)

// ========== 权重仓库 ==========
object WeightRepo {
    fun get(league: League) = when (league) {
        League.KOREA -> WeightMatrix(0.07, 0.10, 0.12, 0.22, 0.18, 0.12)
        League.EUROPE -> WeightMatrix(0.18, 0.20, 0.12, 0.22, 0.18, 0.12)
        League.FINLAND -> WeightMatrix(0.28, 0.10, 0.12, 0.22, 0.18, 0.12)
        League.SWEDEN -> WeightMatrix(0.20, 0.20, 0.12, 0.22, 0.18, 0.12)
        League.NORWAY -> WeightMatrix(0.32, 0.18, 0.12, 0.22, 0.18, 0.12)
        League.BRAZIL -> WeightMatrix(0.36, 0.18, 0.12, 0.22, 0.18, 0.12)
    }
}

// ========== 预测引擎 ==========
class Predictor {
    fun predict(league: League, home: String, away: String, rankDiff: Int? = null): PredictResult {
        val w = WeightRepo.get(league)
        var homeScore = w.homeAdvantage
        var awayScore = 0.0
        if (rankDiff != null && kotlin.math.abs(rankDiff) > 5) {
            if (rankDiff < 0) homeScore += w.strongWin else awayScore += w.strongWin
        }
        val total = homeScore + awayScore + 0.5
        val h = homeScore / total
        val a = awayScore / total
        val d = 1.0 - h - a
        val pred = when {
            h > a && h > d -> "主胜"
            a > h && a > d -> "客胜"
            else -> "平局"
        }
        val factors = listOf(
            "主场优势" to w.homeAdvantage,
            "强队碾压" to w.strongWin,
            "爆冷信号" to w.upsetSignal,
            "初指定位" to w.initialOdds,
            "市场热度" to w.marketReverse
        ).sortedByDescending { it.second }.take(3)
        return PredictResult(home, away, h, d, a, pred, factors)
    }
}

// ========== UI 界面 ==========
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DongGeHongTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App()
                }
            }
        }
    }
}

@Composable
fun App() {
    var selectedLeague by remember { mutableStateOf(League.KOREA) }
    var homeTeam by remember { mutableStateOf("") }
    var awayTeam by remember { mutableStateOf("") }
    var rankDiff by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<PredictResult?>(null) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("东哥红 V3.12", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00D4FF))
        Spacer(modifier = Modifier.height(8.dp))
        Text("多模型协同推演系统", fontSize = 14.sp, color = Color.Gray)

        Spacer(modifier = Modifier.height(16.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(League.values()) { league ->
                FilterChip(
                    selected = league == selectedLeague,
                    onClick = { selectedLeague = league },
                    label = { Text(league.display) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(value = homeTeam, onValueChange = { homeTeam = it },
            label = { Text("主队名称") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = awayTeam, onValueChange = { awayTeam = it },
            label = { Text("客队名称") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = rankDiff, onValueChange = { rankDiff = it },
            label = { Text("排名差（可选）") }, modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(12.dp))

        Button(onClick = {
            if (homeTeam.isNotBlank() && awayTeam.isNotBlank()) {
                val diff = rankDiff.toIntOrNull()
                result = Predictor().predict(selectedLeague, homeTeam, awayTeam, diff)
            }
        }, modifier = Modifier.fillMaxWidth()) {
            Text("🚀 开始预测")
        }

        Spacer(modifier = Modifier.height(16.dp))

        result?.let { res ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
                modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("${res.home} vs ${res.away}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                        listOf("主胜" to res.homeProb, "平局" to res.drawProb, "客胜" to res.awayProb).forEach { (label, prob) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${(prob * 100).toInt()}%", fontSize = 24.sp, color = Color(0xFF00D4FF))
                                Text(label, fontSize = 14.sp, color = Color.Gray)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("预测：${res.prediction}", fontSize = 18.sp,
                        color = when(res.prediction) {
                            "主胜" -> Color.Green
                            "客胜" -> Color.Red
                            else -> Color.Yellow
                        })
                    Text("核心因子：${res.topFactors.joinToString { "${it.first}(${(it.second*100).toInt()}%)" }}",
                        fontSize = 12.sp, color = Color.Gray)
                }
            }
        }
    }
}

// ========== 主题 ==========
@Composable
fun DongGeHongTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF00D4FF),
            background = Color(0xFF0F0F1A),
            surface = Color(0xFF1A1A2E),
            onBackground = Color.White,
            onSurface = Color.White
        ),
        content = content
    )
}

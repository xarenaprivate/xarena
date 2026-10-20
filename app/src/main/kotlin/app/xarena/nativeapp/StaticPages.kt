package app.xarena.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* Static helper pages - real steps matching the site's rules and how-to-join content. No backend. */

@Composable
fun PreMatchChecklistScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Match Day Checklist", "Be ready before the room opens", onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SmallCard {
                Text("One hour before", color = Gold2, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                listOf(
                    "Check in from Match Center - 'I'm here' closes before start",
                    "Charge above 50% and plug in if you can",
                    "Update Free Fire - no last-minute downloads",
                    "Close other apps (2GB phones need the free RAM)"
                ).forEach { Text("- " + it, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp)) }
            } }
            item { SmallCard {
                Text("When the room opens", color = Gold2, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                listOf(
                    "Room ID and password appear in Match Center (registered players only)",
                    "Tap to copy, paste in Free Fire's custom room",
                    "Join with your registered IGN - wrong IGN = no points",
                    "Squad up with your own team before the timer starts"
                ).forEach { Text("- " + it, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp)) }
            } }
            item { SmallCard {
                Text("After the match", color = Gold2, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                listOf(
                    "Upload your result screenshot in Match Center",
                    "The owner reviews it and awards points",
                    "Results land on the leaderboard and your profile"
                ).forEach { Text("- " + it, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp)) }
            } }
        }
    }
}

@Composable
fun HinglishGuideScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Naya Player? Shuru Yahan Se", "Beginner guide in Hinglish", onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SmallCard {
                Text("Account kaise banaye", color = Gold2, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                listOf(
                    "Account page par sign up karo - bilkul free hai",
                    "Phone verification poora karo (OTP aayega)",
                    "Apna Free Fire IGN aur UID sahi-sahi likho"
                ).forEach { Text("- " + it, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp)) }
            } }
            item { SmallCard {
                Text("Tournament kaise khele", color = Gold2, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                listOf(
                    "Tournaments me jao, khula hua tournament chuno",
                    "Apni squad register karo - entry free hai",
                    "Match day par Room ID aur password milega",
                    "Room me time par enter karo aur khelo!"
                ).forEach { Text("- " + it, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp)) }
            } }
            item { SmallCard {
                Text("Points aur levels", color = Gold2, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                listOf(
                    "Roz check-in karo - free XP milta hai",
                    "Matches khelo, leaderboard par upar aao",
                    "Badges aur frames unlock hote hain - sab cosmetic, paise nahi lagte"
                ).forEach { Text("- " + it, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp)) }
            } }
            item { SmallCard {
                Text("Dikkaat aaye to", color = Gold2, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                listOf(
                    "Match Center me report option hai",
                    "Rules page me saare niyam likhe hain",
                    "World Chat me @luna se pooch sakte ho"
                ).forEach { Text("- " + it, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp)) }
            } }
        }
    }
}

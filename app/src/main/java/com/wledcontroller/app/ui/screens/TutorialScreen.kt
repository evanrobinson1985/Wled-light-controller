@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.wledcontroller.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** One page of the walk-through: what a feature is for, how to use it, and where to try it. */
private class TutorialPage(
    val icon: ImageVector,
    val title: String,
    val intro: String,
    val steps: List<String>,
    /** Where "Try it" goes (a screen of the app), or null when there's nothing to open. */
    val route: String? = null,
    val tip: String? = null,
)

private val Pages = listOf(
    TutorialPage(
        Icons.Filled.School, "Welcome",
        "This app runs your WLED lights — one strip or a whole house show — and adds xLights-style shows, a house map, schedules and more. This walk-through takes a couple of minutes.",
        listOf(
            "Swipe, or use Next and Back, to move between pages.",
            "\"Try it\" opens that part of the app; press Back to come back here.",
            "The list button at the top jumps to any topic.",
            "Find this again any time: the ? button on the home screen, or Settings › Tutorial.",
        ),
        tip = "No lights set up yet? The simulator (last pages) gives you a virtual house to try everything on.",
    ),
    TutorialPage(
        Icons.Filled.Router, "Add your controllers",
        "Every WLED controller on your Wi-Fi is found for you.",
        listOf(
            "Tap the magnifier on the home screen to search your network.",
            "Tap Add next to each controller, or Add all.",
            "Not found? Type its IP address (shown in WLED's own settings) instead.",
            "On the home screen, use a controller's switch to turn it on or off, or tap its card to open it.",
            "The power button at the top switches everything off.",
        ),
        route = "home",
    ),
    TutorialPage(
        Icons.Filled.Palette, "Colours & brightness",
        "Open a controller to control it. The Colours tab sets its colours.",
        listOf(
            "Drag on the colour wheel, or type a hex colour.",
            "Slots 1–3 are the main, background and third colour that effects use.",
            "Star colours to keep them as favourites; recent colours are kept for you.",
            "The slider at the top sets brightness; the moon button sets a sleep timer that fades the lights off.",
        ),
    ),
    TutorialPage(
        Icons.Filled.AutoAwesome, "Effects & palettes",
        "The Effects tab lists every effect on the controller.",
        listOf(
            "Tap an effect to play it. Its settings (speed, intensity and extras) open right under it.",
            "Tap the playing effect again to fold its settings away; \"Its settings\" at the top jumps back to them.",
            "Search, or filter by favourites (star), 2D or sound-reactive (♪) effects.",
            "The Palettes tab picks the colours effects blend through, with a preview of each.",
        ),
        tip = "Whatever you choose is what the controller starts with when it's powered on (Settings › Start up with the last effect).",
    ),
    TutorialPage(
        Icons.Filled.ViewTimeline, "Segments",
        "Split one strip into parts that each run their own effect and colours.",
        listOf(
            "Segments tab › Add, then set where each part starts and stops.",
            "Tap a segment to select it: colours and effects then change only the selected ones.",
            "Tick the box on several segments to change them together.",
        ),
    ),
    TutorialPage(
        Icons.Filled.Bookmarks, "Presets & playlists",
        "Presets save a whole look on the controller; playlists cycle through presets.",
        listOf(
            "Presets tab › Save current look as preset.",
            "Tap a preset to bring it back; playlists show with a play icon.",
            "Presets live on the controller, so they also work from WLED's own app and buttons.",
        ),
    ),
    TutorialPage(
        Icons.Filled.Tune, "LED setup & light types",
        "Tell the app what lights are connected so effects suit them.",
        listOf(
            "In a controller, menu (⋮) › LED setup & light type.",
            "Pick the light type — LED strip, bullet / pixel string or matrix / panel — to get sensible speeds and effect suggestions.",
            "Set the LED chip, LED count, colour order, which end the controller is on and the power limit, then Save & reboot.",
        ),
    ),
    TutorialPage(
        Icons.Filled.Celebration, "Holiday themes",
        "Ready-made colour sets for whichever holiday is coming up.",
        listOf(
            "Colours tab (or a group) › Holiday themes.",
            "Tap a theme, pick a suggested effect, and apply.",
            "Themes ramp up as the holiday gets closer.",
        ),
    ),
    TutorialPage(
        Icons.Filled.Workspaces, "Groups & scenes",
        "Control several controllers as one, and save looks across all of them.",
        listOf(
            "Home › Groups › New group, then pick its controllers.",
            "A group has one brightness, colour and effect for all its members, with each effect's settings under it.",
            "Scenes › Save current captures every controller's look at once; tap the scene later to bring it all back.",
        ),
        route = "home",
    ),
    TutorialPage(
        Icons.Filled.GridView, "Quick Settings tile",
        "Switch a controller on or off from the Android quick settings panel.",
        listOf(
            "In a controller, menu (⋮) › Use for Quick Settings tile.",
            "Pull down the notification shade, tap the pencil and drag the WLED tile in.",
        ),
    ),
    TutorialPage(
        Icons.Filled.Home, "The house map",
        "A photo of your house with every light run drawn on it, so effects and shows sweep across the real shape of your home.",
        listOf(
            "Whole house › Edit map › add a photo of your house.",
            "Set the scale: the ruler button, tap two points a known distance apart (in feet and inches).",
            "Add a run for each strip: choose its controller and LEDs, then tap along where the lights go.",
            "Give a run its LED spacing and the lights are placed that far apart; mark stretches hidden round a corner or behind a porch.",
            "\"Flip start\" reverses which end is LED 1; \"Identify\" lights a run so you can check it.",
        ),
        route = "house/map",
    ),
    TutorialPage(
        Icons.Filled.CameraAlt, "Camera scan",
        "Let the camera find where every LED is.",
        listOf(
            "Edit map › camera button (Scan with camera). Best after dark with other lights off.",
            "Prop the phone up so it sees the whole house and keep it still.",
            "All your lights go dark, then each LED flashes on its own until the camera has seen it light and go out again.",
            "Afterwards, nudge or fill in any LEDs in the map editor.",
        ),
        route = "house/scan",
    ),
    TutorialPage(
        Icons.Filled.Category, "Props & vendor models",
        "Arches, stars, trees, matrices, singing faces and vendors' props, laid out LED by LED.",
        listOf(
            "Edit map › Import › Prop from a vendor: pick a maker, category and prop. With the scale set it goes on at its real size with one tap.",
            "Or import an xLights .xmodel, or your whole xLights layout.",
            "Mirror flips a prop left ↔ right; Mirrored copy makes the other one of a pair (a right corner web for a left one).",
            "Parts (submodels) let shows light pieces of a prop on their own.",
        ),
        route = "house/map",
    ),
    TutorialPage(
        Icons.Filled.Home, "Whole-house effects",
        "Effects that flow across every controller at once, following the map.",
        listOf(
            "Home › Whole house: pick an effect and colours, set the speed.",
            "Holiday shadows: the house glows while ghosts, clowns, spiders, witches, Santa's sleigh, bunnies and more drift across it as shadows; set their size, number and speed.",
            "Watch the preview, then press Play to send it to your lights.",
            "It keeps running with the screen off.",
        ),
        route = "house",
    ),
    TutorialPage(
        Icons.Filled.PhoneAndroid, "Run without the phone",
        "Make one controller the master so WLED's own 2D effects span the whole house even when your phone is off.",
        listOf(
            "Whole house › Run without the phone.",
            "Pick the master; the app works out the layout and sets everything up.",
        ),
        route = "house/standalone",
    ),
    TutorialPage(
        Icons.Filled.Movie, "Shows",
        "Build light shows to music on a timeline, like xLights.",
        listOf(
            "Shows › New show, then add a song.",
            "Add tracks for props, groups or parts of props, and drop effects on the timeline.",
            "Layer tracks with blend modes, shape speed and size with curves, and add fades in and out.",
            "Effects include text, pictures, sound-reactive meters, singing faces, and pan / tilt for moving heads.",
            "Play it on the house preview, then on your lights.",
        ),
        route = "shows",
    ),
    TutorialPage(
        Icons.Filled.SwapHoriz, "xLights sequences",
        "Bring work over from xLights and send shows back.",
        listOf(
            "Shows › import button › xLights sequence (.xsq) turns it into a show you can edit.",
            "Import › Rendered sequence (.fseq) plays an xLights show exactly as made, once its models are matched to your controllers.",
            "A show's menu › Export .fseq makes a file for Falcon Player and other players.",
        ),
        route = "shows",
    ),
    TutorialPage(
        Icons.Filled.Schedule, "Schedule",
        "Run your shows automatically every night.",
        listOf(
            "Home › Schedule: make a playlist of shows.",
            "Set when it runs — a clock time, or sunset / dusk with an offset — and for which dates (e.g. December only).",
            "Set your location once so sunset times are right.",
            "Turn the schedule on; it runs in the background.",
        ),
        route = "schedule",
    ),
    TutorialPage(
        Icons.Filled.SettingsInputComponent, "Other controllers & DMX",
        "Falcon, Kulp, ESPixelStick and the like, plus DMX pars, washes and moving heads.",
        listOf(
            "Settings › Other controllers: add E1.31 (sACN), Art-Net or DDP outputs; they go on the map like WLED ones.",
            "Settings › DMX fixtures: patch each fixture's universe and address, from a preset or the fixture library.",
            "Fixtures take their colour from shows and effects; moving heads aim with pan and tilt.",
        ),
        route = "settings",
    ),
    TutorialPage(
        Icons.Filled.Public, "Control from anywhere",
        "Use your lights away from home.",
        listOf(
            "At home the app uses Wi-Fi. Away, it tries each controller's remote address (VPN), then an MQTT broker.",
            "Settings › Control from anywhere explains both: Tailscale is the most secure; MQTT needs no extra hardware.",
            "Set a controller's remote address or MQTT topic in its menu › Remote access.",
            "A controller shows \"via Remote address\" or \"via MQTT\" when it's reached that way.",
        ),
        route = "settings",
    ),
    TutorialPage(
        Icons.Filled.Science, "The simulator",
        "A virtual house with simulated controllers, to try every feature without real lights.",
        listOf(
            "Tap the flask on the home screen, then Start the simulator. Your real setup is saved and comes back when you leave.",
            "Add a prop from a vendor: it gets its own simulated controller and goes on the house at real size.",
            "Arrange props: tap a prop, drag to move it, pinch to resize, Mirror or Mirrored copy.",
            "A small live view of the house follows you round the app.",
        ),
        route = "sim",
    ),
    TutorialPage(
        Icons.Filled.Settings, "Settings & backups",
        "Everything else, and keeping your work safe.",
        listOf(
            "Back up: one file with your controllers, map, photo, shows and songs, schedules and settings. Restore it on a new phone.",
            "Start up with the last effect, light size in previews, and a lights delay to line up the show with the music.",
        ),
        route = "settings",
    ),
    TutorialPage(
        Icons.Filled.Lightbulb, "You're ready",
        "That's everything. Have fun with your lights!",
        listOf(
            "This walk-through is always there: the ? button on the home screen, or Settings › Tutorial.",
        ),
    ),
)

/**
 * The walk-through of every feature: shown on first launch and from the home screen's ? button
 * or Settings. [onTry] opens a screen of the app; [onClose] ends the tutorial.
 */
@Composable
fun TutorialScreen(onClose: () -> Unit, onTry: (route: String) -> Unit) {
    val pager = rememberPagerState { Pages.size }
    val scope = rememberCoroutineScope()
    var contents by remember { mutableStateOf(false) }
    val last = pager.currentPage == Pages.lastIndex

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tutorial · ${pager.currentPage + 1} of ${Pages.size}") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close tutorial") } },
                actions = { IconButton(onClick = { contents = true }) { Icon(Icons.AutoMirrored.Filled.List, "All topics") } },
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                // Where you are: a dot per page.
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.Center) {
                    Pages.indices.forEach { i ->
                        Box(
                            Modifier.padding(horizontal = 2.dp).size(if (i == pager.currentPage) 8.dp else 5.dp).clip(CircleShape)
                                .background(if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (pager.currentPage > 0) {
                        OutlinedButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null); Spacer(Modifier.width(6.dp)); Text("Back")
                        }
                    } else TextButton(onClick = onClose) { Text("Skip") }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { if (last) onClose() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
                        Text(if (last) "Done" else "Next")
                        if (!last) { Spacer(Modifier.width(6.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null) }
                    }
                }
            }
        },
    ) { padding ->
        HorizontalPager(pager, Modifier.padding(padding).fillMaxSize()) { i -> PageContent(Pages[i], onTry) }
    }

    if (contents) {
        AlertDialog(
            onDismissRequest = { contents = false },
            title = { Text("Topics") },
            text = {
                LazyColumn {
                    itemsIndexed(Pages) { i, p ->
                        Row(
                            Modifier.fillMaxWidth().clickable { contents = false; scope.launch { pager.scrollToPage(i) } }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(p.icon, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(p.title, fontWeight = if (i == pager.currentPage) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { contents = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun PageContent(page: TutorialPage, onTry: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(88.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(page.icon, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(page.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(page.intro, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            page.steps.forEachIndexed { n, step ->
                Row(verticalAlignment = Alignment.Top) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(26.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text("${n + 1}", style = MaterialTheme.typography.labelLarge) }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(step, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(top = 3.dp))
                }
            }
        }
        page.tip?.let {
            Spacer(Modifier.height(20.dp))
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                Text("Tip: $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(14.dp))
            }
        }
        page.route?.let { route ->
            Spacer(Modifier.height(24.dp))
            FilledTonalButton(onClick = { onTry(route) }) { Text("Try it") }
        }
    }
}

package io.keval.apps.numbcrunch

import java.io.IOException
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.os.bundleOf
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.navOptions
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchOnWood
import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.domain.progress.RoundMode
import io.keval.apps.numbcrunch.gameplay.progress.NewRoundRequest
import io.keval.apps.numbcrunch.gameplay.progress.RoundDestination
import io.keval.apps.numbcrunch.gameplay.progress.RoundEntryViewModel
import io.keval.apps.numbcrunch.gameplay.progress.RoundEntryDialogs
import io.keval.apps.numbcrunch.gameplay.statistics.StatisticsRoute
import io.keval.apps.numbcrunch.gameplay.navigation.STATISTICS_ROUTE
import io.keval.apps.numbcrunch.gameplay.R
import io.keval.apps.numbcrunch.gameplay.about.AboutScreen
import io.keval.apps.numbcrunch.gameplay.common.NumbCrunchScreenScaffold
import io.keval.apps.numbcrunch.gameplay.game.GameRoute
import io.keval.apps.numbcrunch.gameplay.home.HomeRoute
import io.keval.apps.numbcrunch.gameplay.info.GameplayInfoScreen
import io.keval.apps.numbcrunch.gameplay.navigation.ABOUT_ROUTE
import io.keval.apps.numbcrunch.gameplay.navigation.FIRST_RUN_TUTORIAL_ROUTE
import io.keval.apps.numbcrunch.gameplay.navigation.GAME_DIFFICULTY_ARGUMENT
import io.keval.apps.numbcrunch.gameplay.navigation.GAME_OWNER_ARGUMENT
import io.keval.apps.numbcrunch.gameplay.navigation.GAME_ROUND_ARGUMENT
import io.keval.apps.numbcrunch.gameplay.navigation.GAMEPLAY_INFO_ROUTE
import io.keval.apps.numbcrunch.gameplay.navigation.GAME_ROUTE
import io.keval.apps.numbcrunch.gameplay.navigation.HOME_ROUTE
import io.keval.apps.numbcrunch.gameplay.navigation.LAUNCH_GATE_ROUTE
import io.keval.apps.numbcrunch.gameplay.navigation.MANUAL_TUTORIAL_ROUTE
import io.keval.apps.numbcrunch.gameplay.navigation.PRIVACY_POLICY_ROUTE
import io.keval.apps.numbcrunch.gameplay.navigation.SETTINGS_ROUTE
import io.keval.apps.numbcrunch.gameplay.privacy.PrivacyPolicyScreen
import io.keval.apps.numbcrunch.gameplay.settings.SettingsRoute
import io.keval.apps.numbcrunch.gameplay.navigation.TUTORIAL_ROUTE
import io.keval.apps.numbcrunch.gameplay.navigation.WELCOME_ROUTE
import io.keval.apps.numbcrunch.gameplay.tutorial.TutorialScreen
import io.keval.apps.numbcrunch.gameplay.welcome.WelcomeScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TRANSITION_DURATION_MS = 320

private fun slideInFromRight(): EnterTransition = slideInHorizontally(
    animationSpec = tween(TRANSITION_DURATION_MS),
    initialOffsetX = { fullWidth -> fullWidth },
)

private fun slideOutToLeft(): ExitTransition = slideOutHorizontally(
    animationSpec = tween(TRANSITION_DURATION_MS),
    targetOffsetX = { fullWidth -> -fullWidth / 4 },
)

private fun slideInFromLeft(): EnterTransition = slideInHorizontally(
    animationSpec = tween(TRANSITION_DURATION_MS),
    initialOffsetX = { fullWidth -> -fullWidth / 4 },
)

private fun slideOutToRight(): ExitTransition = slideOutHorizontally(
    animationSpec = tween(TRANSITION_DURATION_MS),
    targetOffsetX = { fullWidth -> fullWidth },
)

@Composable
fun NumbCrunchNavHost(
    navController: NavHostController,
    appContainer: AppContainer,
    modifier: Modifier = Modifier,
) {
    val roundEntry: RoundEntryViewModel = viewModel(factory = RoundEntryViewModel.factory(appContainer.progressRepository))
    val progressState by roundEntry.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(progressState.destination) {
        progressState.destination?.let { destination ->
            roundEntry.consumeDestination(destination)
            navController.navigateToRound(destination) {
                when (navController.currentDestination?.route) {
                    GAME_ROUTE -> popUpTo(GAME_ROUTE) { inclusive = true }
                    TUTORIAL_ROUTE -> popUpTo(TUTORIAL_ROUTE) { inclusive = true }
                }
                launchSingleTop = true
            }
        }
    }
    NavHost(
        navController = navController,
        startDestination = LAUNCH_GATE_ROUTE,
        modifier = modifier,
    ) {
        composable(LAUNCH_GATE_ROUTE) {
            FirstRunLaunchGate(appContainer) { tutorialCompleted ->
                navController.navigate(if (tutorialCompleted) HOME_ROUTE else WELCOME_ROUTE) {
                    popUpTo(LAUNCH_GATE_ROUTE) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }

        composable(
            route = WELCOME_ROUTE,
            enterTransition = { slideInFromRight() },
            exitTransition = { slideOutToLeft() },
            popEnterTransition = { slideInFromLeft() },
            popExitTransition = { slideOutToRight() },
        ) { entry ->
            val scope = rememberCoroutineScope()
            var completionResolved by remember { mutableStateOf(false) }
            var isCompleting by remember { mutableStateOf(false) }
            var completionError by rememberSaveable { mutableStateOf(false) }
            val goHome = {
                if (navController.currentBackStackEntry == entry) {
                    navController.navigate(HOME_ROUTE) {
                        popUpTo(WELCOME_ROUTE) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            // A restored destination may outlive a completed preference write before navigation.
            if (!completionResolved) {
                FirstRunLaunchGate(appContainer) { completed ->
                    if (completed) goHome() else completionResolved = true
                }
            } else {
                WelcomeScreen(
                    onViewTutorial = {
                        if (!isCompleting && navController.currentBackStackEntry == entry) {
                            navController.navigate(FIRST_RUN_TUTORIAL_ROUTE) {
                                popUpTo(WELCOME_ROUTE) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    },
                    onGoHome = {
                        if (!isCompleting && navController.currentBackStackEntry == entry) {
                            isCompleting = true
                            completionError = false
                            scope.launch {
                                try {
                                    appContainer.completeFirstRunTutorialUseCase()
                                    goHome()
                                } catch (_: IOException) {
                                    completionError = true
                                } finally {
                                    isCompleting = false
                                }
                            }
                        }
                    },
                    isCompleting = isCompleting,
                    completionError = completionError,
                )
            }
        }

        composable(
            route = HOME_ROUTE,
            enterTransition = { slideInFromLeft() },
            exitTransition = { slideOutToLeft() },
            popEnterTransition = { slideInFromLeft() },
            popExitTransition = { slideOutToRight() },
        ) {
            HomeRoute(
                getWelcomeMessageUseCase = appContainer.getWelcomeMessageUseCase,
                onStartClick = { homeState ->
                    val secret = if (homeState.playerMode == io.keval.apps.numbcrunch.gameplay.home.PlayerMode.DOUBLE) {
                        homeState.friendSecretNumber
                    } else {
                        null
                    }
                    roundEntry.requestStart(NewRoundRequest(
                        mode = RoundMode.valueOf(homeState.playerMode.name), difficulty = homeState.difficulty,
                        friendSecret = secret,
                    ))
                },
                onTutorialClick = { navController.navigate(MANUAL_TUTORIAL_ROUTE) },
                onGameplayClick = { navController.navigate(GAMEPLAY_INFO_ROUTE) },
                onAboutClick = { navController.navigate(ABOUT_ROUTE) },
                onSettingsClick = { navController.navigate(SETTINGS_ROUTE) { launchSingleTop = true } },
                onStatisticsClick = { navController.navigate(STATISTICS_ROUTE) { launchSingleTop = true } },
                ongoingRounds = progressState.ongoingRounds,
                onResumeRound = roundEntry::resume,
                progressLoadFailed = progressState.hasReadError,
                onRetryProgress = roundEntry::refresh,
            )
        }

        composable(
            route = TUTORIAL_ROUTE,
            arguments = listOf(navArgument("firstRun") {
                type = NavType.BoolType
                defaultValue = false
            }),
            enterTransition = { slideInFromRight() },
            exitTransition = { slideOutToLeft() },
            popEnterTransition = { slideInFromLeft() },
            popExitTransition = { slideOutToRight() },
        ) { entry ->
            val firstRun = entry.arguments?.getBoolean("firstRun") == true
            val scope = rememberCoroutineScope()
            var completionResolved by remember { mutableStateOf(!firstRun) }
            var isCompleting by remember { mutableStateOf(false) }
            var completionError by rememberSaveable { mutableStateOf(false) }
            val goHome = {
                if (navController.currentBackStackEntry == entry) {
                    navController.navigate(HOME_ROUTE) {
                        popUpTo(TUTORIAL_ROUTE) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            val completeFirstRun = {
                if (firstRun && !isCompleting && navController.currentBackStackEntry == entry) {
                    isCompleting = true
                    completionError = false
                    scope.launch {
                        try {
                            appContainer.completeFirstRunTutorialUseCase()
                            goHome()
                        } catch (_: IOException) {
                            completionError = true
                        } finally {
                            isCompleting = false
                        }
                    }
                }
            }
            if (!completionResolved) {
                FirstRunLaunchGate(appContainer) { completed ->
                    if (completed) goHome() else completionResolved = true
                }
            } else {
                TutorialScreen(
                    onBack = {
                        if (firstRun) completeFirstRun() else navController.navigateUp()
                    },
                    onPlay = {
                        roundEntry.requestStart(NewRoundRequest(RoundMode.SINGLE, Difficulty.HARD))
                    },
                    isFirstRun = firstRun,
                    onCompleteFirstRun = completeFirstRun,
                    isCompletingFirstRun = isCompleting,
                    firstRunCompletionError = completionError,
                )
            }
        }

        composable(
            route = GAMEPLAY_INFO_ROUTE,
            enterTransition = { slideInFromRight() },
            exitTransition = { slideOutToLeft() },
            popEnterTransition = { slideInFromLeft() },
            popExitTransition = { slideOutToRight() },
        ) {
            GameplayInfoScreen(onBack = navController::navigateUp)
        }

        composable(
            route = ABOUT_ROUTE,
            enterTransition = { slideInFromRight() },
            exitTransition = { slideOutToLeft() },
            popEnterTransition = { slideInFromLeft() },
            popExitTransition = { slideOutToRight() },
        ) {
            AboutScreen(
                onBack = navController::navigateUp,
                onPrivacyPolicyClick = { navController.navigate(PRIVACY_POLICY_ROUTE) { launchSingleTop = true } },
            )
        }

        composable(
            route = PRIVACY_POLICY_ROUTE,
            enterTransition = { slideInFromRight() },
            exitTransition = { slideOutToLeft() },
            popEnterTransition = { slideInFromLeft() },
            popExitTransition = { slideOutToRight() },
        ) {
            PrivacyPolicyScreen(onBack = navController::navigateUp)
        }

        composable(
            route = SETTINGS_ROUTE,
            enterTransition = { slideInFromRight() },
            exitTransition = { slideOutToLeft() },
            popEnterTransition = { slideInFromLeft() },
            popExitTransition = { slideOutToRight() },
        ) {
            SettingsRoute(
                observeAudioSettings = appContainer.observeAudioSettingsUseCase,
                setMusicEnabled = appContainer.setMusicEnabledUseCase,
                setSoundEffectsEnabled = appContainer.setSoundEffectsEnabledUseCase,
                onBack = navController::navigateUp,
            )
        }

        composable(
            route = STATISTICS_ROUTE,
            enterTransition = { slideInFromRight() },
            exitTransition = { slideOutToLeft() },
            popEnterTransition = { slideInFromLeft() },
            popExitTransition = { slideOutToRight() },
        ) {
            StatisticsRoute(appContainer.progressRepository, navController::navigateUp)
        }

        composable(
            route = GAME_ROUTE,
            arguments = listOf(navArgument(GAME_DIFFICULTY_ARGUMENT) {
                type = NavType.StringType
                defaultValue = Difficulty.HARD.name
            }, navArgument(GAME_OWNER_ARGUMENT) { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument(GAME_ROUND_ARGUMENT) { type = NavType.StringType; nullable = true; defaultValue = null }),
            enterTransition = { slideInFromRight() },
            exitTransition = { slideOutToLeft() },
            popEnterTransition = { slideInFromLeft() },
            popExitTransition = { slideOutToRight() },
        ) { entry ->
            GameRoute(
                progressRepository = appContainer.progressRepository,
                ownerId = entry.arguments?.getString(GAME_OWNER_ARGUMENT),
                roundId = entry.arguments?.getString(GAME_ROUND_ARGUMENT),
                onRequestNewRound = roundEntry::requestStart,
                friendSecretNumber = appContainer.getFriendSecretUseCase(),
                difficulty = Difficulty.fromName(entry.arguments?.getString(GAME_DIFFICULTY_ARGUMENT))
                    ?: Difficulty.HARD,
                onExitToHome = {
                    // Home is normally below Game; if not, open it so leaving can never strand the player.
                    if (!navController.popBackStack(HOME_ROUTE, inclusive = false)) {
                        navController.navigate(HOME_ROUTE) {
                            popUpTo(GAME_ROUTE) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
                // The Game entry stays on the back stack, so its ViewModel (and round) survive Help.
                onHelp = { navController.navigate(GAMEPLAY_INFO_ROUTE) { launchSingleTop = true } },
                onSettings = { navController.navigate(SETTINGS_ROUTE) { launchSingleTop = true } },
            )
        }
    }
    RoundEntryDialogs(progressState, roundEntry)
}

private fun NavHostController.navigateToRound(
    round: RoundDestination,
    options: NavOptionsBuilder.() -> Unit = {},
) {
    // Keep the original destination ID so pre-difficulty back stacks can still restore.
    val destination = checkNotNull(graph.findNode(GAME_ROUTE))
    navigate(destination.id, bundleOf(GAME_OWNER_ARGUMENT to round.ownerId, GAME_ROUND_ARGUMENT to round.roundId), navOptions(options))
}

@Composable
private fun FirstRunLaunchGate(
    appContainer: AppContainer,
    onResolved: (Boolean) -> Unit,
) {
    var retry by remember { mutableIntStateOf(0) }
    var loadFailed by remember { mutableStateOf(false) }
    LaunchedEffect(retry) {
        try {
            onResolved(appContainer.observeFirstRunTutorialUseCase().first())
        } catch (_: IOException) {
            loadFailed = true
        }
    }
    NumbCrunchScreenScaffold {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                stringResource(if (loadFailed) R.string.tutorial_load_error else R.string.tutorial_loading),
                color = NumbCrunchOnWood,
            )
            if (loadFailed) {
                Button(onClick = {
                    loadFailed = false
                    retry++
                }) {
                    Text(stringResource(R.string.tutorial_retry))
                }
            }
        }
    }
}

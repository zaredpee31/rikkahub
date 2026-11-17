package me.rerere.rikkahub.di

import me.rerere.rikkahub.ui.pages.assistant.AssistantVM
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantDetailVM
import me.rerere.rikkahub.ui.pages.backup.BackupVM
import me.rerere.rikkahub.ui.pages.chat.ChatDrawerVM
import me.rerere.rikkahub.ui.pages.chat.ChatVM
import me.rerere.rikkahub.ui.pages.debug.DebugVM
import me.rerere.rikkahub.ui.pages.extensions.PromptVM
import me.rerere.rikkahub.ui.pages.extensions.skills.SkillDetailVM
import me.rerere.rikkahub.ui.pages.extensions.skills.SkillsVM
import me.rerere.rikkahub.ui.pages.extensions.workspace.WorkspaceDetailVM
import me.rerere.rikkahub.ui.pages.extensions.workspace.WorkspaceSshEditorVM
import me.rerere.rikkahub.ui.pages.extensions.workspace.WorkspaceVM
import me.rerere.rikkahub.ui.pages.favorite.FavoriteVM
import me.rerere.rikkahub.ui.pages.history.HistoryVM
import me.rerere.rikkahub.ui.pages.memory.MemoryFileVM
import me.rerere.rikkahub.ui.pages.search.SearchVM
import me.rerere.rikkahub.ui.pages.setting.SettingVM
import me.rerere.rikkahub.ui.pages.share.handler.ShareHandlerVM
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModel<ChatVM> { params ->
        ChatVM(
            id = params.get(),
            context = get(),
            settingsStore = get(),
            chatService = get(),
            updateChecker = get(),
            analytics = get(),
            filesManager = get(),
            favoriteRepository = get(),
        )
    }
    viewModelOf(::ChatDrawerVM)
    viewModelOf(::SettingVM)
    viewModelOf(::DebugVM)
    viewModelOf(::HistoryVM)
    viewModelOf(::AssistantVM)
    viewModel<AssistantDetailVM> {
        AssistantDetailVM(
            id = it.get(),
            settingsStore = get(),
            memoryRepository = get(),
            coreProfile = get(),
            filesManager = get(),
            skillManager = get(),
            workspaceRepository = get(),
        )
    }
    viewModel<MemoryFileVM> {
        MemoryFileVM(
            memoryId = it[0],
            path = it[1],
            memoryRepository = get(),
        )
    }
    viewModel<ShareHandlerVM> {
        ShareHandlerVM(
            text = it.get(),
            settingsStore = get(),
        )
    }
    viewModelOf(::BackupVM)
    viewModelOf(::PromptVM)
    viewModelOf(::SkillsVM)
    viewModelOf(::SkillDetailVM)
    viewModelOf(::WorkspaceVM)
    viewModel<WorkspaceDetailVM> {
        WorkspaceDetailVM(
            id = it.get(),
            repository = get(),
            terminalSessionManager = get(),
        )
    }
    viewModel<WorkspaceSshEditorVM> {
        WorkspaceSshEditorVM(
            id = it.getOrNull(),
            repository = get(),
        )
    }
    viewModelOf(::FavoriteVM)
    viewModelOf(::SearchVM)
}

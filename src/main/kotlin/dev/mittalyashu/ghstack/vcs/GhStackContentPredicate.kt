package dev.mittalyashu.ghstack.vcs

import com.intellij.openapi.project.Project
import git4idea.repo.GitRepositoryManager
import java.util.function.Predicate

class GhStackContentPredicate : Predicate<Project> {
    override fun test(project: Project): Boolean {
        return GitRepositoryManager.getInstance(project).repositories.isNotEmpty()
    }
}

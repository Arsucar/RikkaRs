package me.rerere.workspace

import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceShellPolicyTest {
    @Test
    fun allowsCommonDevCommands() {
        val allowed = listOf(
            "ls -la",
            "cat README.md",
            "grep -r TODO .",
            "pwd",
            "cd ../build/",
        )
        for (cmd in allowed) {
            assertTrue("$cmd should be allowed", evaluateShellCommand(cmd) is ShellCommandVerdict.Allowed)
        }
    }

    @Test
    fun rejectsSensitivePathsAndDestructiveCommands() {
        val rejected = listOf(
            "cat /data/data/me.arsucar.rikka/shared_prefs/foo.xml",
            "rm -rf /",
            "mkfs.ext4 /dev/sda1",
            "dd if=/dev/zero of=/dev/sda",
            "cat ../../data/data/me.arsucar.rikka/foo.xml",
        )
        for (cmd in rejected) {
            assertTrue("$cmd should be rejected", evaluateShellCommand(cmd) is ShellCommandVerdict.Rejected)
        }
    }

    @Test
    fun rejectsExtendedDestructiveAndPowerCommands() {
        val rejected = listOf(
            "rm -rf /*",
            "rm -rf /home",
            "rm -rf ~",
            "rm -rf \$HOME",
            "chmod -R 777 /",
            "reboot",
            ":(){ :|:& };:",
        )
        for (cmd in rejected) {
            assertTrue("$cmd should be rejected", evaluateShellCommand(cmd) is ShellCommandVerdict.Rejected)
        }
    }

    @Test
    fun allowsExtendedDevCommandsWithoutFalsePositives() {
        val allowed = listOf(
            "npm test",
            "find . -name '*.kt'",
            "grep shutdown log.txt",
            "cd ../build/ && ls",
        )
        for (cmd in allowed) {
            assertTrue("$cmd should be allowed", evaluateShellCommand(cmd) is ShellCommandVerdict.Allowed)
        }
    }
}
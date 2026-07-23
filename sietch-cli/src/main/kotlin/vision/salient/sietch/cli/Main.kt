package vision.salient.sietch.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands

class SietchCli : CliktCommand(
    name = "sietch",
    help = "IPFS-backed universal content index"
) {
    override fun run() = Unit
}

fun main(args: Array<String>) {
    SietchCli()
        .subcommands(
            IndexCommand(),
            PinCommand(),
            FetchCommand(),
            PullCommand(),
            ResolveCommand(),
            ListCommand(),
            VerifyCommand(),
            CatalogCommand(),
            StatusCommand()
        )
        .main(args)
}

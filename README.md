# mcdev-mcp

A purpose-built Model Context Protocol (MCP) server for Minecraft mod development.

## Frequently Asked Questions

See [FAQ.md](FAQ.md).

## Setup

1. Build either a native binary or a JAR:
    - Native (requires GraalVM installed): `./mvnw -Pnative package`, produces `target/mcdev-mcp` as output
    - Standard: `./mvnw package`, produces JAR file in `target/` as output
2. Add the appropriate command as a local MCP server to your favorite agentic harness. For the standard JAR, you will
   want to use a command like `java -Xmx32M -jar <path-to-jar>`; for the native binary you can just use it directly.
3. If your harness has a permissions system, whitelist reading from `/tmp/mcdev-mcp` folder and all tools from mcdev-mcp to
   avoid nuisance permission prompts. For Claude Code, this can be done with a block like the following in `~/.claude/settings.json`:
   ```json
   "permissions": {
     "allow": [
       "Read(//tmp/mcdev-mcp/**)",
       "mcp__mcdev-mcp"
     ]
   }
   ```

## System requirements

The MCP server assumes that `java` is available on the PATH and can be used to run other subprocesses like the decompiler.

## Credits

`mcdev-mcp` uses code from or otherwise directly depends on several other projects:

- [NeoFormRuntime](https://github.com/neoforged/NeoFormRuntime/) for obtaining source code of modern Minecraft versions (>=1.17)
- [ZSON](https://github.com/Nolij/ZSON) for JSON parsing and serialization

## License

`mcdev-mcp` is distributed under the OSL-3.0 license.
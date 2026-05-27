**Why build a new MCP server?**

There are two other actively developed MCP servers I'm aware of, both of which serve as inspiration for what I've built here:

* https://github.com/klikli-dev/minecraft-dev-mcp
* https://github.com/Mattabase/modlens-mcp

I chose to build my own for a few reasons.

First, both of them are built in Node.js, which is a common choice for MCP servers because of the maturity of the tooling, but
prevents the reuse of existing modding libraries, which are written in Java. It also makes the codebase harder to extend or modify
for modders unfamiliar with Java.

Second, the other servers are complex and contain many tools I do not feel are necessary for the agent (at least for
my personal use cases) or that I believe are better expressed as skills or CLI commands.
This means they have large codebases and are difficult to audit for correctness and maintainability, especially for modders unfamiliar with JavaScript toolchains.

Both servers provide significant inspiration and are much more complete projects. I recommend checking them out if `mcdev-mcp` does not suit your requirements.

**Why MCP rather than a CLI or skill?**

This is a very good question. Mario Zechner (creator of the Pi coding agent) outlined [numerous disadvantages of MCP](https://mariozechner.at/posts/2025-11-02-what-if-you-dont-need-mcp/)
in a blog post. In the context of coding agents, MCPs are often redundant since the agent still needs access to a shell
and a filesystem to get anything meaningful done, so Mario recommends CLIs as a replacement.

The problem with CLIs is twofold: one needs to exist for the given domain, and the agent needs to be made aware that it
exists. Modding currently lacks a standard CLI tool, so even the CLI approach would require building a dedicated program like
`mcdev-cli` from scratch.

To inform the agent about CLIs not in its training data and to provide context about when the CLI would be useful, the
current convention is to use a skill. This has problems of its own: the agent must voluntarily choose to load the skill,
and then voluntarily choose to run the commands documented inside.

I actually built a CLI/skill hybrid and used it for many months: a `minecraft-moddev` skill providing important context
about modding, and a Gradle init script that could be injected into a project and print out the path to Minecraft sources.
It breaks down in two ways. Most fundamentally, the hybrid model cannot support referencing source code from a *different*
Minecraft version than the one used in the current project — there is no project context to inject into. More generally,
I often had to word my prompts carefully or explicitly remind the agent to use the skill in order to locate sources at all.

A local (stdio) MCP server solves many of these problems.

* MCP system instructions are typically injected into the agent's system prompt, which means the agent will always be aware of
conventions and workflows I outline there, without me needing to import a separate file into my personal global CLAUDE.md.
* I can encapsulate complex workflows like "get Minecraft 1.20.1 sources with Forge 47.4.0" into a single tool, which
reduces the number of tokens the agent must emit to obtain a folder with decompiled source code
* Since the MCP server runs locally as a sidecar alongside the agent, it has access to the same filesystem, meaning it
can dump sources to a folder which the agent then works with using `Bash` (avoiding the composability problems of typical MCPs, where tools return data the agent must relay rather than act on directly)

**Why use Java?**

Java may seem like an unusual choice for a minimal MCP server, but the entire Minecraft ecosystem is built around it.
This means I can introduce utilities like a mapping tool in the future without needing to ship a Java bundle alongside
the MCP server.

The MCP server doesn't need to be fast (heavy lifting like decompilation is done in an ephemeral JVM process
to keep the main server process lightweight) and uses very little runtime reflection, which makes it a natural candidate for
GraalVM's native compiler. The resulting binary starts nearly instantly and uses very little RAM (20MB on fresh launch). 
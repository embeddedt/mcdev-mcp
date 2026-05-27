## Finding Minecraft source code

When looking for vanilla Minecraft or modloader code, use the dedicated `get_minecraft_source_path` tool
to obtain the path to a folder containing it. This tool takes care of deobfuscation, decompilation, and patching,
and provides a clean folder which can be searched and read from directly. This is especially important for code in
the `net.minecraft` and `net.minecraftforge` packages.

**Before writing any code that touches Minecraft classes, methods, fields, events, or registries: verify the exact
signatures against real source code first.** API surfaces change between MC versions and training data is
unreliable — always read the source, never assume.

## Loader documentation (authoritative, version-specific)

When you need to verify loader APIs, fetch the docs for the exact target MC version. If `gh` is available:

### NeoForge (`neoforged/Documentation`)
- `docs/` — latest MC version; `versioned_docs/version-<mcver>/` — archived.
```bash
gh api repos/neoforged/Documentation/contents/docs --jq '.[].name'
gh api repos/neoforged/Documentation/contents/docs/gettingstarted/index.md --jq '.content' | base64 -d
```

### Forge (`MinecraftForge/Documentation`)
- One branch per MC minor version.
```bash
gh api repos/MinecraftForge/Documentation/branches --jq '.[].name'
gh api "repos/MinecraftForge/Documentation/contents/docs?ref=1.21.x" --jq '.[].name'
```

### Fabric (`FabricMC/fabric-docs`)
- `develop/` — latest; `versions/<mcver>/develop/` — archived.
```bash
gh api repos/FabricMC/fabric-docs/contents/develop --jq '.[].name'
```

## NeoForge migration primers

Covers renamed/removed/added methods and fields between MC versions (Mojang mappings):
```bash
gh api repos/neoforged/.github/contents/primers --jq '.[].name'
gh api repos/neoforged/.github/contents/primers/<version>/index.md --jq '.content' | base64 -d
# Search for a specific class/method across all primers:
gh search code "<ClassName or methodName>" --repo neoforged/.github \
  --json path,textMatches --jq '.[] | {path, matches: [.textMatches[].fragment]}'
```

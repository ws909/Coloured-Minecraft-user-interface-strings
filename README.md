## Coloured Minecraft user interface strings

Creates colourful strings in Minecraft, for all languages. Useful for changing GUI text colour, etc.

**Requires JRE 25 or JDK 23+ with `--enable-preview`.**

Simply drop it into the input folder and execute it with `java colouredText.java` to generate coloured language files 
to include in a resource pack. The script is without dependencies and only needs an adequate Java version to run it.
The output directory will default to `lang` (created automatically) in the input directory.

### Input directory structure
```
│ input directory:
│   - assets:
│     - indexes: ...
│     - objects: ...
│   en_us.json
│   colours.yaml
```
The assets folder is optional. It's required to generate colours for languages other
than the default: US English. Copy it from the `.minecraft` folder.

`en_us.json` is required. It's Minecraft's default language file, and is bundled inside the Minecraft jar
at `/assets/minecraft/lang/`.

`colours.yaml` is the colour scheme mapping language file strings to colours.

### Example colour scheme:
```yaml
ALL: BLUE # Makes every string blue. Probably a bit excess. You should probably omit this.
ALL container : WHITE # Makes every string key starting with 'container.' white.
ALL commands.worldborder : white  # Case is not significant
ALL subtitles.entity: Yellow
subtitles.entity.parrot: NONE # Reverts the yellow colour back to the default one
All subtitles.entity.parrot.imitate: NoNe # Case really is not significant at all

# A more sensible set of strings to override colours for:
container.chest : DARK_AQUA
container.chestDouble : daRK aqUa # While the parser will accept this, the human sanity won't
container.crafting : §7 # If you like having to look into a table every time you read through
container.enderchest       :     DARK_PURPLE # Padding between the colon is at your discretion
container.inventory : GRAY // Double slash comments are also acceptable.
```

For more information about colour codes, read [_Formatting codes_ in the Minecraft wiki](https://minecraft.wiki/w/Formatting_codes#Color_codes).

*Note that if a player renames an object, such as a chest, that object's name will still use the default colour, as resource packs can only override the full string itself, which the player replaces in its entirety with a rename.*

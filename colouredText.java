import static java.lang.Integer.min;
import static java.util.Map.entry;

//import org.jetbrains.annotations.NotNull;
//import org.jetbrains.annotations.Nullable;

// Didn't want dependencies, and it should be quite clear I'll appreciate proper nullness markers once they arrive.
// Oh, and, non-null by default. Seriously. Exclamation mark should be reserved for nullable, no forced runtime check.
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD,ElementType.FIELD,ElementType.PARAMETER,ElementType.LOCAL_VARIABLE,ElementType.TYPE_USE})
@interface NotNull {}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD,ElementType.FIELD,ElementType.PARAMETER,ElementType.LOCAL_VARIABLE,ElementType.TYPE_USE})
@interface Nullable {}

enum Colour {
    BLACK("§0"),
    DARK_BLUE("§1"),
    DARK_GREEN("§2"),
    DARK_AQUA("§3"),
    DARK_RED("§4"),
    DARK_PURPLE("§5"),
    GOLD("§6"),
    GRAY("§7"),
    DARK_GRAY("§8"),
    BLUE("§9"),
    GREEN("§a"),
    AQUA("§b"),
    RED("§c"),
    LIGHT_PURPLE("§d"),
    YELLOW("§e"),
    WHITE("§f"),
    NONE("");
    
    final @NotNull String code;
    
    Colour(final @NotNull String code) {
        this.code = code;
    }
    
    static @Nullable Colour parse(final @NotNull String string) {
        try {
            if (string.startsWith("§")) {
                final var index = Integer.parseInt(string.substring(1, 2), 16);
                return values()[index];
            }
            return valueOf(string.toUpperCase(Locale.ROOT).replace(" ", "_"));
        } catch (IllegalArgumentException _) {
            return null;
        }
    }
}


static final @NotNull String colourMappingsFileName = "colours.yaml";
static final @NotNull String outputDirectoryName = "lang";

static void main(final @NotNull String[] args) {
    if (args.length > 0 && args[0].equals("-h")) {
        System.out.println(
                """
                There are three optional parameters: the input directory, the output directory, and
                the colour scheme file path.
                The defaults for each is: current working directory, `/lang` in the input directory, and `colours.yaml`
                in the input directory.
                
                | input directory:
                |   - assets:
                |     - indexes: ...
                |     - objects: ...
                |   en_us.json
                |   colours.yaml
                
                The assets folder is optional. It's required to generate colours for all languages other
                than the default: US English. Copy it from the `.minecraft` folder.
                
                en_us.json is required. It's Minecraft's default language file, and is bundled inside the Minecraft jar
                at `/assets/minecraft/lang/`.
                
                The colour scheme is a very simple file listing a key and a value on each line, separated by a colon.
                The key is one of the language string keys found in the language files, while the value is a named
                colour or colour code. Only Java Edition formatting colours are supported. See
                https://minecraft.wiki/w/Formatting_codes#Color_codes for more details.
                
                Empty lines and trailing comments (# and //) are ignored
                
                Prefix the key with `all ` to specify a colour for the entire range of strings within that key
                container.
                The special colour `none` can be used to switch a colour in such a container back to default.
                """
        );
        return;
    }
    
    if (args.length > 3) {
        throw new IllegalArgumentException("Too many arguments");
    }
    
    final var inputDirectory = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
    final var outputDirectory = Path.of((args.length > 1 && !args[1].equals(".")) ? args[1] : outputDirectoryName).toAbsolutePath().normalize();
    
    final var assetsDirectory = inputDirectory.resolve("assets");
    final var indexDirectory = assetsDirectory.resolve( "indexes");
    final var langDirectory = assetsDirectory.resolve("objects");
    
    final var colourMappingsFile = args.length == 3 ? Path.of(args[2]) : inputDirectory.resolve(colourMappingsFileName).toAbsolutePath().normalize();
    final var defaultLangFile = inputDirectory.resolve("en_us.json");
    
    final @NotNull Map<@NotNull String, @NotNull Colour> colourMappings;
    try {
        colourMappings = parseColourScheme(colourMappingsFile);
    } catch (IOException exception) {
        System.err.println("Colour mappings file not found; " + exception.getMessage());
        System.exit(1); return;
    } catch (ParseException exception) {
        System.err.println("Error parsing colour mappings file:\n" + exception);
        System.exit(2); return;
    }
    
    System.out.println(colourMappings.size() + " entries in the colour scheme");
    
    final var fallbackMappings = new HashMap<@NotNull String, @NotNull Colour>();
    colourMappings.entrySet().removeIf(entry -> {
        final var prefix = "ALL ";
        final var key = entry.getKey();
        final var keyStart = key.substring(0, min(key.length(), prefix.length())).toUpperCase(Locale.ROOT);
        
        if (keyStart.startsWith(prefix)) {
            fallbackMappings.put(key.substring(prefix.length()), entry.getValue());
            return true;
        }
        if (keyStart.equals("ALL")) {
            fallbackMappings.put("", entry.getValue());
            return true;
        }
        return false;
    });
    
    final var languageFiles = Files.exists(assetsDirectory)
            ? getLanguageFiles(indexDirectory)
            : new HashMap<@NotNull String, @NotNull Path>(1);
    languageFiles.put("en_us", defaultLangFile);
    
    try {
        Files.createDirectories(outputDirectory);
    } catch (IOException exception) {
        System.out.println("Failed to create output directory: " + exception.getMessage());
        System.exit(1);
    }
    
    try (final var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        for (final var entry : languageFiles.entrySet()) {
            executor.submit(() -> {
                final var path = langDirectory.resolve(entry.getValue());
                try (final var stream = Files.lines(path)) {
                    final var colouredText = stream
                            .map(String::strip)
                            .filter(line -> !(line.isEmpty() || line.equals("{") || line.equals("}")))
                            .map(line -> {
                                final var keyStartIndex = line.indexOf("\"") + 1;
                                final var keyEndIndex = line.indexOf("\"", keyStartIndex);
                                final var valueStartIndex = line.indexOf("\"", keyEndIndex + 1) + 1;
                                final var valueEndIndex = line.lastIndexOf("\"");
                                
                                return entry(
                                        line.substring(keyStartIndex, keyEndIndex),
                                        line.substring(valueStartIndex, valueEndIndex)
                                );
                            })
                            .map(e -> {
                                final var key = e.getKey();
                                
                                @Nullable Colour colour;
                                search: {
                                    if ((colour = colourMappings.get(key)) != null) {
                                        break search;
                                    }
                                    var keyPath = key;
                                    while (true) {
                                        if ((colour = fallbackMappings.get(keyPath)) != null) {
                                            break search;
                                        }
                                        final var index = keyPath.lastIndexOf(".");
                                        if (index == -1) {
                                            break;
                                        }
                                        
                                        keyPath = key.substring(0, index);
                                    }
                                    colour = fallbackMappings.get("");
                                }
                                
                                return switch (colour) {
                                    case null -> null;
                                    case Colour.NONE -> null;
                                    default -> entry(key, colour.code + e.getValue());
                                };
                            })
                            .filter(Objects::nonNull)
                            .toList();
                    
                    final var outputFile = outputDirectory.resolve(entry.getKey() + ".json");
                    final var encoder = StandardCharsets.UTF_8.newEncoder();
                    try (
                            final var out = Files.newOutputStream(
                                    outputFile,
                                    StandardOpenOption.CREATE,
                                    StandardOpenOption.WRITE,
                                    StandardOpenOption.TRUNCATE_EXISTING
                            );
                            final var writer = new BufferedWriter(new OutputStreamWriter(out, encoder))
                    ) {
                        writer.append("{");
                        for (var i = 0; i < colouredText.size(); ++i) {
                            final var e = colouredText.get(i);
                            writer
                                    .append("\n    \"")
                                    .append(e.getKey())
                                    .append("\": \"")
                                    .append(e.getValue())
                                    .append("\"");
                            
                            if (i != colouredText.size() - 1) {
                                writer.append(",");
                            }
                        }
                        writer.append("\n}");
                    }
                } catch (IOException exception) {
                    System.err.println("Failed to generate language files: " + exception.getMessage());
                    System.exit(1);
                }
            });
        }
    }
    
    System.out.println(languageFiles.size() + " language files generated in " + outputDirectory);
}

private static @NotNull Map<@NotNull String, @NotNull Path> getLanguageFiles(final @NotNull Path indexDirectory) {
    final @NotNull Path indexFile;
    
    try (final var stream = Files.list(indexDirectory)) {
        indexFile = stream.filter(path -> path.toFile().isFile()).sorted().reduce((_, e) -> e).orElseThrow();
        if (!indexFile.toFile().getName().endsWith(".json")) {
            throw new NoSuchElementException();
        }
    } catch (IOException exception) {
        System.err.println(exception.getMessage());
        System.exit(1); return null;
    } catch (NoSuchElementException exception) {
        System.err.println("No language index file available");
        System.exit(1); return null;
    }
    
    final @NotNull Map<@NotNull String, @Nullable Path> languageFiles;
    try {
        languageFiles = extractLanguageFileNames(indexFile);
    } catch (IOException exception) {
        System.err.println(exception.getMessage());
        System.exit(1); return null;
    }
    
    final var missingLanguages = languageFiles.entrySet().stream()
            .map(e -> {
                if (e.getValue() == null) {
                    return e.getKey();
                }
                return null;
            })
            .filter(Objects::nonNull).toList();
    
    System.out.println((languageFiles.size() - missingLanguages.size()) + " cached languages found");
    
    if (!missingLanguages.isEmpty()) {
        missingLanguages.forEach(languageFiles.keySet()::remove);
        
        System.out.println(missingLanguages.size() + " languages have no cached language file: " + missingLanguages);
    }
    
    //noinspection NullableProblems
    return languageFiles;
}

private static @NotNull Map<@NotNull String, @Nullable Path> extractLanguageFileNames(
        final @NotNull Path indexFile
) throws IOException {
    final var string = Files.readString(indexFile);
    final var keyPrefix = "\"minecraft/lang/";
    final var keyValueSeparator = ".json\": {\"hash\": \"";
    final var valueSuffix = "\"";
    
    final var map = new HashMap<@NotNull String, @Nullable Path>();
    
    var keyIndex = string.indexOf(keyPrefix);
    while (keyIndex >= 0) {
        final var separatorIndex = string.indexOf(keyValueSeparator, keyIndex);
        final var valueEndIndex = string.indexOf(valueSuffix, separatorIndex + keyValueSeparator.length());
        
        final var key = string.substring(keyIndex + keyPrefix.length(), separatorIndex);
        final var value = string.substring(separatorIndex + keyValueSeparator.length(), valueEndIndex);
        
        if (value.isEmpty()) {
            map.put(key, null);
        } else {
            map.put(key, Path.of(value.substring(0, 2), value));
        }
        
        keyIndex = string.indexOf(keyPrefix, valueEndIndex);
    }
    
    return map;
}

record ParsedLine<T>(
        int lineNumber, @NotNull String original, @NotNull T parsed
) {
    
    static ParsedLine<String> initial(final int lineNumber, final @NotNull String line) {
        return new ParsedLine<>(lineNumber, line, line);
    }
    
    <R> ParsedLine<R> with(final @NotNull R parsedLine) {
        return new ParsedLine<>(lineNumber, original, parsedLine);
    }
}

static class ParseException extends RuntimeException {
    
    final @NotNull ParsedLine<?> line;
    
    ParseException(final @NotNull String reason, final @NotNull ParsedLine<?> line) {
        super(reason);
        this.line = line;
    }
    
    @Override
    public @NotNull String toString() {
        return getMessage() + " at line " + (line.lineNumber + 1) + "; \"" + line.original + "\"";
    }
}

static @NotNull Map<@NotNull String, @NotNull Colour> parseColourScheme(
        final @NotNull Path path
) throws IOException, ParseException {
    final var lines = Files.readAllLines(path);
    
    @SuppressWarnings("unchecked")
    final ParsedLine<String>[] indexedLines = new ParsedLine[lines.size()];
    
    for (var i = 0; i < lines.size(); ++i) {
        indexedLines[i] = ParsedLine.initial(i, lines.get(i));
    }
    
    final var scheme = new HashMap<@NotNull String, @NotNull Colour>(lines.size(), 1);
    
    Arrays.stream(indexedLines)
            // Double-slash comments aren't valid YAML, but the user might supply any type of file, and choose freely
            .map(line -> line.with(line.parsed.split("#", 2)[0])) // Trailing comment
            .map(line -> line.with(line.parsed.split("//", 2)[0])) // Trailing comment
            .filter(line -> !line.parsed.isBlank())
            .map(line -> {
                final var components = line.parsed.split(":");
                
                if (components.length != 2) {
                    throw new ParseException("Syntax error", line);
                }
                
                final var key   = components[0].strip();
                final var value = components[1].strip();
                
                if (key.isEmpty() || value.isEmpty()) {
                    throw new ParseException("Syntax error", line);
                }
                
                final var colour = Colour.parse(value);
                
                if (colour == null) {
                    throw new ParseException("Invalid colour", line);
                }
                
                return line.with(entry(key, colour));
            })
            .forEach(line -> {
                if (scheme.put(line.parsed.getKey(), line.parsed.getValue()) != null) {
                    throw new ParseException("Duplicate entry", line);
                }
            });
    
    return scheme;
}

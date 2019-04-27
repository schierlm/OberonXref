package oberonxref;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import oberonxref.Symbol.SymbolType;

public class UnreferencedCodeFinder {
	public static void main(String[] args) throws Exception {
		if (args.length != 1) {
			System.out.println("Usage: java -cp OberonXref.jar oberonxref.UnreferencedCodeFinder <indir>");
			return;
		}
		List<String> modQueue = new ArrayList<>(), nextModQueue = new ArrayList<>();
		Map<String, List<Symbol>> parsedModules = new HashMap<>();
		Map<String, List<String>> importedModules = new HashMap<>();
		System.out.println("=== Scanning Modules ===");
		for (File f : new File(args[0]).listFiles()) {
			if (f.getName().endsWith(".Mod.txt")) {
				String code = new String(Files.readAllBytes(f.toPath()), "ISO-8859-1").trim();
				List<Symbol> syms = new ArrayList<>();
				Scanner.scan(code, syms, new ArrayList<>());
				List<String> imports = new ArrayList<>();
				String modname = Parser.findImportedModules(syms, imports);
				importedModules.put(modname, imports);
				modQueue.add(modname);
				parsedModules.put(modname, syms);
			}
		}
		System.out.println("=== Parsing Modules ===");
		Map<String, Scope> publicScopes = new HashMap<>();
		for (String builtin : Arrays.asList("BUILTINS", "SYSTEM")) {
			try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
					InputStream in = Scanner.class.getResourceAsStream("/" + builtin + ".Mod.txt")) {
				HTMLBuilder.copyStream(in, baos);
				String code = new String(baos.toByteArray(), "ISO-8859-1").trim();
				List<Symbol> syms = new ArrayList<>();
				Scanner.scan(code, syms, new ArrayList<>());
				new Parser(syms, publicScopes).parseModule();
				parsedModules.put(builtin, syms);
			}
		}
		Map<String, List<String>> filesToCheck = new HashMap<>();
		while (!modQueue.isEmpty()) {
			for (String mod : modQueue) {
				String lastUnresolvedImport = null;
				for (String dep : importedModules.get(mod))
					if (!publicScopes.containsKey(dep))
						lastUnresolvedImport = dep;
				if (lastUnresolvedImport == null) {
					List<Symbol> symbols = parsedModules.get(mod);
					Parser p = new Parser(symbols, publicScopes);
					try {
						p.parseModule();
						List<String> nextMods = filesToCheck.remove(mod);
						if (nextMods != null)
							nextModQueue.addAll(nextMods);
					} catch (RuntimeException ex) {
						// find approximate error position
						int nextSym = p.getNextSym();
						StringBuilder symTexts = new StringBuilder();
						for (int i = Math.max(0, nextSym - 25); i < Math.min(symbols.size(), nextSym + 25); i++) {
							if (i == nextSym)
								symTexts.append("\nSymbols after:\n");
							symTexts.append(symbols.get(i).getText() + " ");
						}
						throw new RuntimeException("While parsing " + mod + "\nSymbols before:\n" + symTexts.toString(), ex);
					}
				} else {
					filesToCheck.computeIfAbsent(lastUnresolvedImport, m -> new ArrayList<>()).add(mod);
				}
			}
			modQueue = nextModQueue;
			nextModQueue = new ArrayList<>();
		}
		if (!filesToCheck.isEmpty())
			throw new RuntimeException("Unsatisfiable module dependencies: " + filesToCheck);
		System.out.println("=== Checking inside module ===");
		Map<String, Set<String>> publicLinkReferences = new HashMap<>();
		for (Map.Entry<String, List<Symbol>> e : parsedModules.entrySet()) {
			boolean lastWasEnd = false;
			List<String> definedSymbols = new ArrayList<>();
			Set<String> usedSymbols = new HashSet<>();
			for (Symbol s : e.getValue()) {
				if (s.getType().equals(SymbolType.IDENTIFIER)) {
					if (s.getLink() == null)
						throw new RuntimeException(e.getKey() + ": No link on " + s);
				} else if (!s.getType().equals(SymbolType.OP_TIMES) && s.getLink() != null) {
					throw new RuntimeException(e.getKey() + ": Link on " + s);
				}
				if (s.getLink() != null) {
					if (s.getLink().startsWith("#")) {
						if (!lastWasEnd)
							usedSymbols.add(s.getLink().substring(1));
					} else if (s.getLink().startsWith("=")) {
						if (!s.getLink().equals("=MODULE"))
							definedSymbols.add(s.getLink().substring(1));
					} else if (s.getLink().startsWith(e.getKey() + "-usage.html#")) {
						usedSymbols.add(s.getLink().substring((e.getKey() + "-usage.html#").length()));
						publicLinkReferences.putIfAbsent(s.getLink().replace("-usage.html#", ".html#"), new HashSet<>());
					} else {
						publicLinkReferences.computeIfAbsent(s.getLink(), v -> new HashSet<>()).add(e.getKey() + ".html");
					}
				}
				lastWasEnd = s.getType().equals(SymbolType.KEYWORD_END);
			}
			Collections.sort(definedSymbols);
			for (String symbol : definedSymbols) {
				if (!usedSymbols.contains(symbol) && !symbol.contains(".PROCEDURE."))
					System.out.println("~> " + e.getKey() + "." + symbol);
			}
		}
		System.out.println("=== Checking exports ===");
		List<String> usageRefKeys = new ArrayList<>(publicLinkReferences.keySet());
		Collections.sort(usageRefKeys);
		for (String link : usageRefKeys) {
			if (publicLinkReferences.get(link).isEmpty())
				System.out.println("*> " + link.replace(".html#", "."));
		}
	}
}

package oberonxref;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import oberonxref.Symbol.SymbolType;

public class Main {
	public static void main(String[] args) throws Exception {
		if (args.length != 2) {
			System.out.println("Usage: java -jar OberonXref.jar <indir> <outdir>");
			return;
		}
		List<String> modQueue = new ArrayList<>(), nextModQueue = new ArrayList<>();
		Map<String, List<Symbol>> parsedModules = new HashMap<>();
		Map<String, List<Symbol>> parsedModulesWithWhitespace = new HashMap<>();
		Map<String, Disassembly> disassemblyForModules = new HashMap<>();
		Map<String, List<String>> importedModules = new HashMap<>();
		System.out.println("=== Scanning ===");
		for (File f : new File(args[0]).listFiles()) {
			if (f.getName().endsWith(".Mod.txt")) {
				System.out.println(f);
				String code = new String(Files.readAllBytes(f.toPath()), "ISO-8859-1").trim();
				List<Symbol> allSyms = new ArrayList<>(), syms = new ArrayList<>();
				boolean rawStringsFound = Scanner.scan(code, syms, allSyms);
				StringBuilder sb = new StringBuilder();
				for (Symbol sym : allSyms)
					sb.append(sym.getText());
				if (!sb.toString().equals(code)) {
					throw new RuntimeException("Scanner altered the source code!");
				}
				List<String> imports = new ArrayList<>();
				String modname = Parser.findImportedModules(syms, imports);
				if (!f.getName().equals(modname + ".Mod.txt"))
					throw new RuntimeException("Invalid file name " + f.getName() + " for module " + modname);
				importedModules.put(modname, imports);
				modQueue.add(modname);
				parsedModules.put(modname, syms);
				parsedModulesWithWhitespace.put(modname, allSyms);
				File rscFile = new File(f.getParentFile(), modname + ".rsc");
				if (rscFile.exists()) {
					disassemblyForModules.put(modname, Disassembly.disassembly(rscFile, !rawStringsFound));
				} else {
					System.out.println("(no .rsc file");
				}
			}
		}
		System.out.println("=== Parsing ===");
		Map<String, Scope> publicScopes = new HashMap<>();
		for (String builtin : Arrays.asList("BUILTINS", "SYSTEM")) {
			try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
					InputStream in = Scanner.class.getResourceAsStream("/" + builtin + ".Mod.txt")) {
				HTMLBuilder.copyStream(in, baos);
				String code = new String(baos.toByteArray(), "ISO-8859-1").trim();
				List<Symbol> allSyms = new ArrayList<>(), syms = new ArrayList<>();
				Scanner.scan(code, syms, allSyms);
				Parser p = new Parser(syms, publicScopes);
				String modname = p.parseModule();
				if (!builtin.equals(modname))
					throw new RuntimeException("Invalid builtin name " + builtin + " for module " + modname);
				parsedModulesWithWhitespace.put(builtin, allSyms);
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
						System.out.println(mod);
						String modname = p.parseModule();
						if (!mod.equals(modname))
							throw new RuntimeException("Module name " + modname + " does not match " + mod);
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
		System.out.println("=== Building symbol usage lists ===");
		Set<String> globalLinkAnchors = new HashSet<>(), usageLinks = new HashSet<>();
		Map<String, Set<String>> publicLinkReferences = new HashMap<>();
		for (Map.Entry<String, List<Symbol>> e : parsedModulesWithWhitespace.entrySet()) {
			String htmlFile = e.getKey() + ".html";
			List<String> localLinks = new ArrayList<>();
			for (Symbol s : e.getValue()) {
				if (s.getType().equals(SymbolType.IDENTIFIER)) {
					if (s.getLink() == null)
						throw new RuntimeException(e.getKey() + ": No link on " + s);
				} else if (!s.getType().equals(SymbolType.OP_TIMES) && s.getLink() != null) {
					throw new RuntimeException(e.getKey() + ": Link on " + s);
				}
				if (s.getLink() != null) {
					if (s.getLink().startsWith("=")) {
						globalLinkAnchors.add(htmlFile + "#" + s.getLink().substring(1));
					} else if (s.getLink().startsWith("#")) {
						localLinks.add(s.getLink());
					} else if (s.getLink().startsWith(htmlFile.replace(".html", "-usage.html") + "#")) {
						usageLinks.add(s.getLink().replace("-usage.html#", ".html#"));
						publicLinkReferences.putIfAbsent(s.getLink().replace("-usage.html#", ".html#"), new HashSet<>());
					} else {
						publicLinkReferences.computeIfAbsent(s.getLink(), v -> new HashSet<>()).add(htmlFile);
					}
				}
			}
			for (String localLink : localLinks) {
				if (!globalLinkAnchors.contains(htmlFile + localLink)) {
					throw new RuntimeException("Dangling link in " + htmlFile + ": " + localLink);
				}
			}
		}
		for (String globalLink : publicLinkReferences.keySet()) {
			if (!usageLinks.contains(globalLink))
				throw new RuntimeException(globalLink);
			if (!globalLinkAnchors.contains(globalLink))
				System.out.println("Dangling link: " + globalLink);
		}
		for (String usageLink : usageLinks) {
			if (!publicLinkReferences.containsKey(usageLink))
				throw new RuntimeException();
		}
		System.out.println("=== Exporting HTML files ===");
		HTMLBuilder hb = new HTMLBuilder(new File(args[1]));
		hb.copyResource("style.css");
		hb.copyResource("script.js");
		for (String module : parsedModulesWithWhitespace.keySet()) {
			System.out.println(module);
			hb.writeMainFile(module, parsedModulesWithWhitespace.get(module), disassemblyForModules.get(module));
			Map<String, Set<String>> usageRefs = new HashMap<>();
			for (String link : publicLinkReferences.keySet()) {
				if (link.startsWith(module + ".html#")) {
					usageRefs.put(link.substring(module.length() + 6), publicLinkReferences.get(link));
				}
			}
			hb.writeUsageFile(module, usageRefs);
		}
		hb.writeIndexFile(parsedModulesWithWhitespace.keySet(), importedModules);
	}
}

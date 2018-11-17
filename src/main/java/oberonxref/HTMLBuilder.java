package oberonxref;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import oberonxref.Symbol.SymbolType;

public class HTMLBuilder {

	public static void copyStream(InputStream in, OutputStream out) throws IOException {
		byte[] buf = new byte[4096];
		int len;
		while ((len = in.read(buf)) != -1) {
			out.write(buf, 0, len);
		}
	}

	private static String h(String unescaped) {
		return unescaped.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}

	private final File directory;

	public HTMLBuilder(File directory) {
		this.directory = directory;
	}

	public void copyResource(String filename) throws IOException {
		try (InputStream in = HTMLBuilder.class.getResourceAsStream("/" + filename);
				OutputStream out = new FileOutputStream(new File(directory, filename))) {
			copyStream(in, out);
		}
	}

	public void writeMainFile(String module, List<Symbol> source, Disassembly disassembly) throws IOException {
		TreeMap<Integer, List<Integer>> disassemblyRefs = new TreeMap<>();
		Map<String, Set<String>> allExternalLinks = new HashMap<>();
		if (disassembly != null) {
			for (int i = 0; i < disassembly.codePositions.length; i++) {
				int val = disassembly.codePositions[i];
				disassemblyRefs.computeIfAbsent(val, k -> new ArrayList<>()).add(i);
			}
		}
		List<Integer> allCodePositions = new ArrayList<>(disassemblyRefs.keySet());
		Map<Integer, Integer> mapCodePositionToIndex = new HashMap<>();
		for (int i = 0; i < allCodePositions.size(); i++) {
			mapCodePositionToIndex.put(allCodePositions.get(i), i);
		}
		for (Symbol sym : source) {
			if (sym.getLink() != null && sym.getLink().contains(".html#") && !sym.getLink().contains("-usage.html#")) {
				String[] parts = sym.getLink().split("\\.html#");
				allExternalLinks.computeIfAbsent(parts[0], s -> new HashSet<>()).add(parts[1]);
			}
		}
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(directory, module + ".html")), StandardCharsets.UTF_8))) {
			bw.write("<!DOCTYPE html><html><head>\n");
			bw.write("<meta charset=\"UTF-8\">\n");
			bw.write("<title>" + h(module) + " &ndash; OberonXref</title>\n");
			bw.write("<link rel=\"stylesheet\" type=\"text/css\" href=\"style.css\">\n");
			bw.write("<script type=\"text/javascript\" src=\"script.js\"></script>\n");
			bw.write("</head><body><h1>" + h(module) + "</h1><p id=\"togglebuttons\"></p>\n");
			bw.write("<p class=\"usedexports\"><h2>Used Exports</h2>");
			List<String> allExternalLinkFiles = new ArrayList<>(allExternalLinks.keySet());
			Collections.sort(allExternalLinkFiles);
			for (String lf : allExternalLinkFiles) {
				bw.write("<b>" + h(lf) + ":</b>");
				List<String> links = new ArrayList<>(allExternalLinks.get(lf));
				Collections.sort(links);
				for (String link : links) {
					bw.write(" <a name=\"X_" + h(lf) + "_" + h(link) + "\" href=\"" + h(lf) + ".html#" + h(link) + "\">" + h(link) + "</a>");
				}
				bw.write("<br>");
			}
			bw.write("</p>\n");
			bw.write("<table class=\"sourcecode showsource" + (disassembly != null ? " assemblypresent" : "") + "\">\n");
			int[] assemblyOffsets = new int[] { -1, -1, -1 };
			int sourceLine = 1, sourcePos = 0, sourceCharPos = 0;
			int nextAssemblyCharPos = disassemblyRefs.isEmpty() ? Integer.MAX_VALUE : disassemblyRefs.firstKey();
			while (sourcePos < source.size()) {
				bw.write("<tr class=\"sl\"><th><a name=\"L_" + sourceLine + "\" href=\"#L_" + sourceLine + "\" data-l=\"" + sourceLine + "\"></th><td>");
				sourceLine++;
				while (sourcePos < source.size()) {
					Symbol s = source.get(sourcePos);
					sourcePos++;
					String endTag = "";
					if (nextAssemblyCharPos == sourceCharPos) {
						writeDisassemblyRefs(bw, mapCodePositionToIndex.get(nextAssemblyCharPos), disassemblyRefs.remove(nextAssemblyCharPos), assemblyOffsets);
						nextAssemblyCharPos = disassemblyRefs.isEmpty() ? Integer.MAX_VALUE : disassemblyRefs.firstKey();
					}
					if (s.getLink() != null) {
						String href = "href=\"" + h(s.getLink()) + "\"";
						if (s.getLink().startsWith("=")) {
							href = "name=\"" + h(s.getLink().substring(1)) + "\" href=\"#" + h(s.getLink().substring(1)) + "\"";
						}
						if (!s.getCSS().isEmpty())
							href += " style=\"" + h(s.getCSS()) + "\"";
						bw.write("<a " + href + ">");
						endTag = "</a>";
					} else if (!s.getCSS().isEmpty()) {
						bw.write("<span style=\"" + h(s.getCSS()) + "\">");
						endTag = "</span>";
					}
					String textToPrint = s.getText();
					while (nextAssemblyCharPos < sourceCharPos + textToPrint.length()) {
						int prefixLen = nextAssemblyCharPos - sourceCharPos;
						if (!textToPrint.substring(0, prefixLen).equals("\n"))
							bw.write(h(textToPrint.substring(0, prefixLen)));
						textToPrint = textToPrint.substring(prefixLen);
						sourceCharPos += prefixLen;
						writeDisassemblyRefs(bw, mapCodePositionToIndex.get(nextAssemblyCharPos), disassemblyRefs.remove(nextAssemblyCharPos), assemblyOffsets);
						nextAssemblyCharPos = disassemblyRefs.isEmpty() ? Integer.MAX_VALUE : disassemblyRefs.firstKey();
					}
					sourceCharPos += textToPrint.length();
					if (!textToPrint.equals("\n"))
						bw.write(h(textToPrint));
					if (sourcePos == source.size()) {
						if (nextAssemblyCharPos == sourceCharPos) {
							writeDisassemblyRefs(bw, mapCodePositionToIndex.get(nextAssemblyCharPos), disassemblyRefs.remove(nextAssemblyCharPos), assemblyOffsets);
							nextAssemblyCharPos = disassemblyRefs.isEmpty() ? Integer.MAX_VALUE : disassemblyRefs.firstKey();
						}
					}
					bw.write(endTag);
					if (s.getType() == SymbolType.SKIP_LINEBREAK) {
						break;
					}
				}
				bw.write("</td></tr>\n");
				if (disassembly != null && assemblyOffsets[2] == -1) {
					assemblyOffsets[2] = 0;
					printAssembly(bw, -1, null, -1, disassembly.introductionLines.toArray(new String[disassembly.introductionLines.size()]));
				}
				while (assemblyOffsets[1] < assemblyOffsets[0]) {
					assemblyOffsets[1]++;
					printAssembly(bw, assemblyOffsets[1], mapCodePositionToIndex, disassembly.codePositions[assemblyOffsets[1]], disassembly.codeLines[assemblyOffsets[1]]);
				}
			}
			while (disassembly != null && assemblyOffsets[1] < disassembly.codeLines.length - 1) {
				assemblyOffsets[1]++;
				printAssembly(bw, assemblyOffsets[1], mapCodePositionToIndex, disassembly.codePositions[assemblyOffsets[1]], disassembly.codeLines[assemblyOffsets[1]]);
			}
			if (!disassemblyRefs.isEmpty())
				throw new RuntimeException(sourceCharPos + "/" + disassemblyRefs);
			bw.write("</table></body></html>\n");
		}
	}

	private void writeDisassemblyRefs(BufferedWriter bw, int letterIndex, List<Integer> disasmRefs, int[] assemblyOffsets) throws IOException {
		for (Integer ref : disasmRefs) {
			if (assemblyOffsets[0] < ref)
				assemblyOffsets[0] = ref;
		}
		bw.write("<sub><s><u></u><b></b></s><a name=\"#AR_" + letterIndex + "\" href=\"#AR_" + letterIndex + "\">" + (char) ('a' + letterIndex % 26) + "</a></sub>");
	}

	private void printAssembly(BufferedWriter bw, int offset, Map<Integer, Integer> mapCodePositionToIndex, int position, String[] asmLines) throws IOException {
		int letterIndex = mapCodePositionToIndex == null ? -1 : mapCodePositionToIndex.get(position);
		String hexOffset = offset == -1 ? "    " : String.format("%04X", offset);
		for (int i = 0; i < asmLines.length; i++) {
			bw.write("<tr class=\"al\"><th><a name=\"A_" + hexOffset + "_" + (i + 1) + "\" href=\"#A_" + hexOffset + "_" + (i + 1) + "\">" + hexOffset + "</a></th><td><sup");
			if (position != -1)
				bw.write(" title=\"" + position + "\"");
			bw.write(">");
			if (letterIndex == -1)
				bw.write(" ");
			else
				bw.write("<a href=\"#AR_" + letterIndex + "\">" + (char) ('a' + letterIndex % 26) + "</a>");
			bw.write("</sup>" + h(asmLines[i]) + "</td></tr>\n");
		}
	}

	public void writeUsageFile(String module, Map<String, Set<String>> usageRefs) throws IOException {
		List<String> exports = new ArrayList<>(usageRefs.keySet());
		Collections.sort(exports);
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(directory, module + "-usage.html")), StandardCharsets.UTF_8))) {
			bw.write("<!DOCTYPE html><html><head>\n");
			bw.write("<meta charset=\"UTF-8\">\n");
			bw.write("<title>" + h(module) + " Usage &ndash; OberonXref</title>\n");
			bw.write("<link rel=\"stylesheet\" type=\"text/css\" href=\"style.css\">\n");
			bw.write("</head><body><h1>" + h(module) + " Usage</h1>\n");
			for (String export : exports) {
				bw.write("<h2><a name=\"" + h(export) + "\">" + h(export) + "</a></h2><ul>");
				List<String> usages = new ArrayList<>(usageRefs.get(export));
				Collections.sort(usages);
				for (String usage : usages) {
					String usageMod = usage.replaceFirst("\\.html$", "");
					bw.write("<li><a href=\"" + h(usage) + "#X_" + h(module) + "_" + h(export) + "\">" + h(usageMod) + "</a></li>");
				}
				bw.write("</ul>\n");
			}
			bw.write("</body></html>\n");
		}
	}

	public void writeIndexFile(Collection<String> modules, Map<String, List<String>> importedModules) throws IOException {
		List<String> sortedModules = new ArrayList<>(modules);
		Collections.sort(sortedModules);
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(directory, "index.html")), StandardCharsets.UTF_8))) {
			bw.write("<!DOCTYPE html><html><head>\n");
			bw.write("<meta charset=\"UTF-8\">\n");
			bw.write("<title>OberonXref</title>\n");
			bw.write("<link rel=\"stylesheet\" type=\"text/css\" href=\"style.css\">\n");
			bw.write("</head><body><h1>OberonXref</h1>\n");
			for (String module : sortedModules) {
				if (module.equals("BUILTINS") || module.equals("SYSTEM"))
					continue;
				bw.write("<p><b><a href=\"" + h(module) + ".html\">" + h(module) + "</a></b>");
				List<String> imported = importedModules.get(module);
				if (!imported.isEmpty()) {
					bw.write(" (Imports:");
					for (String imp : imported) {
						bw.write(" <a href=\"" + h(imp) + ".html\">" + h(imp) + "</a></b>");
					}
					bw.write(")");
				}
				bw.write("</p>\n");
			}
			bw.write("</body></html>\n");
		}
	}
}

package oberonxref;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Disassembly {

	private static final String[] mnemo0 = {
			"MOV", "LSL", "ASR", "ROR", "AND", "ANN", "IOR", "XOR",
			"ADD", "SUB", "MUL", "DIV", "FAD", "FSB", "FML", "FDV"
	}, mnemo1 = {
			"MI", "EQ", "CS", "VS", "LS", "LT", "LE", "",
			"PL", "NE", "CC", "VC", "HI", "GE", "GT", "NO"
	};

	private static String readString(DataInputStream in) throws IOException {
		StringBuilder sb = new StringBuilder();
		byte b = in.readByte();
		while (b != 0) {
			sb.append((char) (b & 0xFF));
			b = in.readByte();
		}
		return sb.toString();
	}

	static ByteBuffer flipBuffer = ByteBuffer.allocate(4);

	private static int readInt(DataInputStream dis) throws IOException {
		flipBuffer.clear();
		flipBuffer.order(ByteOrder.BIG_ENDIAN);
		flipBuffer.putInt(dis.readInt());
		flipBuffer.flip();
		flipBuffer.order(ByteOrder.LITTLE_ENDIAN);
		return flipBuffer.getInt();
	}

	private static String reg(int r) {
		if (r < 12)
			return "R" + Integer.toHexString(r % 0x10).toUpperCase();
		else if (r == 12)
			return "MT";
		else if (r == 13)
			return "SB";
		else if (r == 14)
			return "SP";
		else if (r == 15)
			return "LNK";
		throw new IllegalArgumentException("Register " + r);
	}

	private static String opcode(int _w, int addr, Fixup fixup, List<String> moduleNames, int data, Map<Integer, String> strings) {
		long w = _w & 0xFFFFFFFFL;
		int k = (int) (w / 0x40000000 % 4);
		int a = (int) (w / 0x1000000 % 0x10);
		int b = (int) (w / 0x100000 % 0x10);
		int op = (int) (w / 0x10000 % 0x10);
		int u = (int) (w / 0x20000000 % 2);
		if (k == 2 && fixup == Fixup.D) {
			int ww = (int) (w % 0x10000);
			if (ww >= 0x8000)
				ww -= 0x10000;
			int mno = b;
			b = 12;
			return String.format("%s %s, %s, MOD%d [%s]", u == 1 ? "STR" : "LDR", reg(a), reg(b), mno, moduleNames.get(mno));
		} else if (k == 2 && fixup == Fixup.D2A) {
			int vno = (int) (w % 0x100);
			boolean proc = (w / 0x100) % 2 == 1;
			return String.format("%s %s, %s, %s%d", u == 1 ? "STR" : "LDR", reg(a), reg(b), proc ? "PROC" : "VAR", vno);
		} else if (k == 1 && fixup == Fixup.D2A) {
			int vno = (int) (w % 0x100);
			boolean proc = (w / 0x100) % 2 == 1;
			return String.format("%s%s %s, %s, %s%d", mnemo0[op], u == 1 ? "'" : "", reg(a), reg(b), proc ? "PROC" : "VAR", vno);
		} else if (k == 1 && fixup == Fixup.D2S) {
			int ww = (int) (w % 0x10000);
			if (ww >= 0x8000)
				ww -= 0x10000;
			String str = "data";
			if (ww >= data) {
				if (strings.get(ww).equals("\0\0"))
					str = "pointer reference";
				else
					str = "\"" + strings.get(ww).toString() + "\"";
			}
			return String.format("%s%s %s, %s, %XH [%s]", mnemo0[op], u == 1 ? "'" : "", reg(a), reg(b), ww, str);
		} else if (k == 2 && fixup == Fixup.D2S) {
			int ww = (int) (w % 0x10000);
			if (ww >= 0x8000)
				ww -= 0x10000;
			String str = "data";
			if (ww >= data) {
				if (strings.get(ww).equals("\0\0"))
					str = "pointer reference";
				else
					str = "\"" + strings.get(ww).toString() + "\"";
			}
			return String.format("%s %s, %s, %XH [%s]", u == 1 ? "STR" : "LDR", reg(a), reg(b), ww, str);
		} else if (k == 0 && fixup == Fixup.D2S) {
			return String.format("%s%s %s, %s, %s [global array]", mnemo0[op], u == 1 ? "'" : "", reg(a), reg(b), reg((int) (w % 0x10)));
		} else if (k == 3 && u == 1 && fixup == Fixup.P) {
			int mno = b;
			int pno = (int) (w / 0x1000) % 0x100;
			return String.format("B%s%s MOD%d [%s] PROC%d", w / 0x10000000 % 2 == 1 ? "L" : "", mnemo1[a].toString(), mno, moduleNames.get(mno), pno);
		} else if (fixup != null) {
			throw new IllegalArgumentException(fixup + "/" + k + "/" + u);
		} else if (k == 0) { // op REG REG REG
			return String.format("%s%s %s, %s, %s", mnemo0[op], u == 1 ? "'" : "", reg(a), reg(b), reg((int) (w % 0x10)));
		} else if (k == 1) { // op REG REG IMM
			int ww = (int) (w % 0x10000);
			if (ww >= 0x8000)
				ww -= 0x10000;
			return String.format("%s%s %s, %s, %XH", mnemo0[op], u == 1 ? "'" : "", reg(a), reg(b), ww);
		} else if (k == 2) { // LDR/STR
			int ww = (int) (w % 0x10000);
			if (ww >= 0x8000)
				ww -= 0x10000;
			return String.format("%s %s, %s, %XH", u == 1 ? "STR" : "LDR", reg(a), reg(b), ww);
		} else /* if (k == 3) */ { // Branch instruction
			if (u == 0) {
				int trap = (int) (w / 0x10) % 0x10;
				int pos = (int) (w / 0x100) % 0x10000;
				return String.format("B%s%s %s%s", w / 0x10000000 % 2 == 1 ? "L" : "", mnemo1[a].toString(), reg((int) (w % 0x10)), trap != 0 || pos != 0 ? " [trap=" + trap + ", pos=" + pos + "]" : "");
			}

			int ww = (int) (w % 0x100000);
			if (ww >= 0x80000)
				ww -= 0x100000;
			return String.format("B%s%s %d [%04XH]", w / 0x10000000 % 2 == 1 ? "L" : "", mnemo1[a].toString(), ww, addr + 1 + ww);
		}
	}

	public static Disassembly disassembly(File file, boolean parseStrings) throws IOException {
		try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
			List<String> lines = new ArrayList<>();
			List<String> moduleNames = new ArrayList<>();
			String moduleName = readString(dis);
			moduleNames.add(moduleName);
			int moduleKey = readInt(dis);
			int version = (int) dis.readByte();
			int size = readInt(dis);
			lines.add(String.format("               .MODULE %s (KEY %08X, VERSION %d, SIZE %08X)", moduleName, moduleKey, version, size));
			String importName;
			while (!(importName = readString(dis)).isEmpty()) {
				moduleNames.add(importName);
				int importKey = readInt(dis);
				lines.add(String.format("               .IMPORT %s (KEY %08X)", importName, importKey));
			}
			int tdCount = readInt(dis) / 4;
			if (tdCount > 0) {
				StringBuilder sb = new StringBuilder();
				sb.append("               .TYPEDESC");
				for (int i = 0; i < tdCount; i++) {
					sb.append(String.format(" %08X", readInt(dis)));
				}
				lines.add(sb.toString());
			}
			int data = readInt(dis);
			if (data != 0)
				lines.add(String.format("               .DATA %XH", data));
			data += tdCount * 4;
			int stringLength = readInt(dis);
			Map<Integer, String> strings = null;
			if (parseStrings) {
				strings = new HashMap<>();
				StringBuilder curr = new StringBuilder();
				for (int i = 0; i < stringLength; i++) {
					char __ch = (char) dis.readUnsignedByte();
					if (__ch == '\0') {
						int offset = data + i - curr.length();
						strings.put(offset, curr.toString());
						lines.add("               .STRING " + String.format("%XH", offset) + " \"" + curr.toString() + "\"");
						curr.setLength(0);
						while (i % 4 != 3) {
							int b = dis.readUnsignedByte();
							if (b != 0)
								throw new IllegalStateException(i + "/" + b + ": " + (char) b);
							i++;
						}
					} else {
						curr.append(__ch);
					}
				}
				if (curr.length() != 0)
					throw new RuntimeException(curr.toString());
			} else {
				lines.add("               .STRING TABLE SKIPPED");
				dis.skip(stringLength);
				data = Integer.MAX_VALUE;
			}
			int[] opcodes = new int[readInt(dis)];
			for (int i = 0; i < opcodes.length; i++) {
				opcodes[i] = readInt(dis);
			}
			Map<String, Integer> commandOffsets = new LinkedHashMap<>();
			String commandName;
			while (!(commandName = readString(dis)).isEmpty()) {
				commandOffsets.put(commandName, readInt(dis));
			}
			int[] entries = new int[readInt(dis)];
			for (int i = 0; i < entries.length; i++) {
				entries[i] = readInt(dis);
			}
			List<Integer> pointerRefs = new ArrayList<>();
			int pointerRef;
			while ((pointerRef = readInt(dis)) != -1) {
				pointerRefs.add(pointerRef);
			}
			if (!pointerRefs.isEmpty()) {
				StringBuilder sb = new StringBuilder();
				sb.append("               .POINTER_REFERENCES");
				for (Integer pref : pointerRefs) {
					sb.append(String.format(" %XH", pref));
					if (strings != null)
						strings.put(pref, "\0\0");
				}
				lines.add(sb.toString());
			}
			int fixP = readInt(dis), fixD = readInt(dis), fixT = readInt(dis), entryPoint = readInt(dis);
			lines.add(String.format("               .FIXUP T %XH", fixT));
			if (dis.readByte() != 'O' || dis.readByte() != 'M')
				throw new EOFException();
			Disassembly result = new Disassembly(opcodes.length);
			for (int i = 0; i < result.codePositions.length; i++) {
				result.codePositions[i] = readInt(dis);
			}
			if (dis.readByte() != 'X' || dis.read() != -1)
				throw new EOFException();
			Fixup[] fixups = new Fixup[opcodes.length];
			while (fixP != 0) {
				if (fixups[fixP] != null)
					throw new IllegalStateException();
				fixups[fixP] = Fixup.P;
				fixP -= opcodes[fixP] & 0xFFF;
			}
			while (fixD != 0) {
				if (fixups[fixD] != null)
					throw new IllegalStateException();
				fixups[fixD] = Fixup.D;
				int mno = (opcodes[fixD] & 0xF00000) / 0x100000 & 0x0F;
				fixups[fixD + 1] = mno == 0 ? Fixup.D2S : Fixup.D2A;
				fixD -= opcodes[fixD] & 0xFFF;
			}
			result.introductionLines.addAll(lines);
			lines.clear();
			for (int i = 0; i < opcodes.length; i++) {
				for (int j = 0; j < entries.length; j++) {
					if (i * 4 == entries[j])
						lines.add(String.format("               .PROC %d", j));
				}
				for (Map.Entry<String, Integer> command : commandOffsets.entrySet()) {
					if (i * 4 == command.getValue())
						lines.add(String.format("               .COMMAND %s", command.getKey()));
				}
				if (i * 4 == entryPoint)
					lines.add("               .ENTRYPOINT");
				if (fixups[i] == Fixup.D || fixups[i] == Fixup.P)
					lines.add(String.format("               .FIXUP %s", fixups[i].name()));
				lines.add(String.format("(%08X):    %s", opcodes[i], opcode(opcodes[i], i, fixups[i], moduleNames, data, strings)));
				result.codeLines[i] = lines.toArray(new String[lines.size()]);
				lines.clear();
			}
			return result;
		}
	}

	private static enum Fixup {
		P, D, D2A, D2S
	}

	public final int[] codePositions;
	public final String[][] codeLines;
	public final List<String> introductionLines = new ArrayList<>();

	public Disassembly(int opcodeSize) {
		codePositions = new int[opcodeSize];
		codeLines = new String[opcodeSize][];
	}
}

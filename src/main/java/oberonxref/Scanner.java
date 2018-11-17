package oberonxref;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import oberonxref.Symbol.SymbolType;

public class Scanner {

	private static SymbolType[] singleSymbols = new SymbolType['~' + 1];
	private static Map<String, SymbolType> doubleSymbols = new HashMap<>();

	static {
		singleSymbols['#'] = SymbolType.OP_NEQ;
		singleSymbols['&'] = SymbolType.OP_AND;
		singleSymbols[')'] = SymbolType.OP_RPAREN;
		singleSymbols['*'] = SymbolType.OP_TIMES;
		singleSymbols['+'] = SymbolType.OP_PLUS;
		singleSymbols[','] = SymbolType.OP_COMMA;
		singleSymbols['-'] = SymbolType.OP_MINUS;
		singleSymbols['/'] = SymbolType.OP_RDIV;
		singleSymbols[';'] = SymbolType.OP_SEMICOLON;
		singleSymbols['='] = SymbolType.OP_EQL;
		singleSymbols['['] = SymbolType.OP_LBRAK;
		singleSymbols[']'] = SymbolType.OP_RBRAK;
		singleSymbols['^'] = SymbolType.OP_ARROW;
		singleSymbols['{'] = SymbolType.OP_LBRACE;
		singleSymbols['}'] = SymbolType.OP_RBRACE;
		singleSymbols['|'] = SymbolType.OP_BAR;
		singleSymbols['~'] = SymbolType.OP_NOT;
		singleSymbols['.'] = SymbolType.OP_PERIOD;
		doubleSymbols.put("..", SymbolType.OP_UPTO);
		singleSymbols[':'] = SymbolType.OP_COLON;
		doubleSymbols.put(":=", SymbolType.OP_BECOMES);
		singleSymbols['>'] = SymbolType.OP_GTR;
		doubleSymbols.put(">=", SymbolType.OP_GEQ);
		singleSymbols['<'] = SymbolType.OP_LSS;
		doubleSymbols.put("<=", SymbolType.OP_LEQ);
	}

	private String text;
	private int nextPos;
	List<Symbol> result = new ArrayList<>();
	boolean rawStringsFound;

	public Scanner(String text) {
		this.text = text;
		this.nextPos = 0;
	}

	public static boolean scan(String text, List<Symbol> meaningfulSymbols, List<Symbol> allSymbols) {
		Scanner s = new Scanner(text);
		s.scan();
		List<Symbol> tmp = s.result;
		allSymbols.addAll(tmp);
		tmp.removeIf(sy -> sy.hasTypeOneOf(SymbolType.SKIP_COMMENT, SymbolType.SKIP_LINEBREAK, SymbolType.SKIP_WHITESPACE));
		meaningfulSymbols.addAll(tmp);
		return s.rawStringsFound;
	}

	private char next() {
		return text.charAt(nextPos);
	}

	private Symbol scanIdentifier() {
		int start = nextPos;
		while (nextPos < text.length()) {
			char ch = next();
			if (ch >= 'A' && ch <= 'Z' || ch >= 'a' && ch <= 'z' || ch >= '0' && ch <= '9') {
				nextPos++;
			} else {
				break;
			}
		}
		String symbolText = text.substring(start, nextPos);
		SymbolType type = symbolText.matches("[A-Z]{2,9}") ? Symbol.KEYWORD_MAP.get(symbolText) : null;
		if (type == null) {
			type = SymbolType.IDENTIFIER;
		}
		return new Symbol(type, symbolText);
	}

	private Symbol makeSymbol(SymbolType type, int start) {
		return new Symbol(type, text.substring(start, nextPos));
	}

	private Symbol scanString() { // both hex and normal ones
		int start = nextPos;
		int end = text.indexOf(text.charAt(start), nextPos + 1);
		if (end == -1)
			throw new RuntimeException("unterminated string");
		nextPos = end + 1;
		return makeSymbol(SymbolType.CONST, start);
	}

	private Symbol scanNumber() {
		int start = nextPos;
		while (next() >= '0' && next() <= '9' || next() >= 'A' && next() <= 'F') {
			nextPos++;
		}
		if (next() == 'H' || next() == 'R' || next() == 'X') {
			nextPos++;
		} else if (next() == '.' && !text.startsWith("..", nextPos)) {
			nextPos++;
			while (next() >= '0' && next() <= '9')
				nextPos++;
			if (next() == 'E' || next() == 'D') {
				nextPos++;
				if (next() == '+' || next() == '-')
					nextPos++;
				if (next() < '0' || next() > '9')
					throw new RuntimeException("digit?");
				while (next() >= '0' && next() <= '9')
					nextPos++;
			}
		}
		return makeSymbol(SymbolType.CONST, start);
	}

	private void scanComment(List<Symbol> result) { // nextPos points to * of (*
		int start = nextPos - 1;
		skipComment();
		String txt = text.substring(start, nextPos);
		int pos = txt.indexOf('\n');
		while (pos != -1) {
			result.add(new Symbol(SymbolType.SKIP_COMMENT, txt.substring(0, pos)));
			result.add(new Symbol(SymbolType.SKIP_LINEBREAK, "\n"));
			txt = txt.substring(pos + 1);
			pos = txt.indexOf('\n');
		}
		result.add(new Symbol(SymbolType.SKIP_COMMENT, txt));
	}

	private void skipComment() {
		nextPos++;
		do {
			while (next() != '*') {
				if (next() == '(') {
					nextPos++;
					if (next() == '*')
						skipComment();
				} else {
					nextPos++;
				}
			}
			while (nextPos < text.length() && next() == '*')
				nextPos++;
		} while (next() != ')');
		nextPos++;
	}

	private void scan() {
		while (nextPos < text.length()) {
			char ch = next();
			if (ch == '\n') {
				result.add(new Symbol(SymbolType.SKIP_LINEBREAK, "\n"));
				nextPos++;
			} else if (ch <= ' ') {
				result.add(new Symbol(SymbolType.SKIP_WHITESPACE, text.substring(nextPos, nextPos + 1)));
				nextPos++;
			} else if (ch >= '0' && ch <= '9') {
				result.add(scanNumber());
			} else if (ch >= 'A' && ch <= 'Z' || ch >= 'a' && ch <= 'z') {
				result.add(scanIdentifier());
			} else if (ch == '"' || ch == '$') {
				if (ch == '$')
					rawStringsFound = true;
				result.add(scanString());
			} else if (ch == '(') {
				nextPos++;
				if (next() == '*') {
					scanComment(result);
				} else {
					result.add(new Symbol(SymbolType.OP_LPAREN, "("));
				}
			} else if (singleSymbols[ch] != null) {
				nextPos++;
				if (nextPos < text.length() && (ch == '.' || ch == ':' || ch == '>' || ch == '<')) {
					SymbolType ds = doubleSymbols.get(text.substring(nextPos - 1, nextPos + 1));
					if (ds != null) {
						nextPos++;
						result.add(makeSymbol(ds, nextPos - 2));
					} else {
						result.add(new Symbol(singleSymbols[ch], "" + ch));
					}
				} else {
					result.add(new Symbol(singleSymbols[ch], "" + ch));
				}
			} else {
				throw new RuntimeException("invalid char " + ch);
			}
		}
	}
}

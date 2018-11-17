package oberonxref;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import oberonxref.Scope.Type;
import oberonxref.Symbol.SymbolType;

public class Parser {

	public static String findImportedModules(List<Symbol> program, List<String> dependenciesToFill) {
		int nextSym = 0;
		while (program.get(nextSym).hasTypeOneOf(SymbolType.KEYWORD_MODULE, SymbolType.OP_TIMES))
			nextSym++;
		if (program.get(nextSym).getType() != SymbolType.IDENTIFIER)
			return "";
		String moduleName = program.get(nextSym).getText();
		nextSym++;
		if (program.get(nextSym).getType() == SymbolType.OP_SEMICOLON)
			nextSym++;
		if (program.get(nextSym).getType() == SymbolType.KEYWORD_IMPORT) {
			nextSym++;
			while (program.get(nextSym).getType() == SymbolType.IDENTIFIER) {
				String importedModule = program.get(nextSym).getText();
				nextSym++;
				if (program.get(nextSym).getType() == SymbolType.OP_BECOMES) {
					nextSym++;
					if (program.get(nextSym).getType() != SymbolType.IDENTIFIER)
						break;
					importedModule = program.get(nextSym).getText();
					nextSym++;
				}
				dependenciesToFill.add(importedModule);
				if (program.get(nextSym).getType() == SymbolType.OP_COMMA)
					nextSym++;
			}
		}
		return moduleName;
	}

	private final List<Symbol> program;
	private final Map<String, Scope> publicScopes;
	private Scope currentScope;
	private int nextSym = 0;

	public Parser(List<Symbol> program, Map<String, Scope> publicScopes) {
		this.program = program;
		this.publicScopes = publicScopes;
		currentScope = new Scope(publicScopes.get("BUILTINS"), "#", null);
	}

	public int getNextSym() {
		return nextSym;
	}

	private Symbol symbol() {
		return program.get(nextSym);
	}

	private SymbolType sym() {
		return symbol().getType();
	}

	private void expect(SymbolType symType, String error, boolean skip) {
		if (sym() != symType)
			throw new RuntimeException(error);
		if (skip)
			nextSym++;
	}

	protected void setDefinitionLink(Scope scope, Symbol symbol) {
		symbol.setLink("=" + scope.getIdentifierLink(symbol.getText()).replaceFirst("^#", ""));
	}

	private boolean checkExport(String identifier) {
		if (sym() == SymbolType.OP_TIMES) {
			Scope pubScope = currentScope.getPublicScope();
			if (pubScope == null)
				throw new RuntimeException("remove asterisk");
			Scope.Type identType = currentScope.getIdentifierType(identifier);
			pubScope.putIdentifier(identifier, identType, null);
			setExportUsageLink(currentScope, symbol(), identifier);
			nextSym++;
			return true;
		}
		return false;
	}

	private TypeAndScope parseQualifiedIdentifier() {
		expect(SymbolType.IDENTIFIER, "identifier expected", false);
		String ident = symbol().getText();
		symbol().setLink(currentScope.getIdentifierLink(ident));
		TypeAndScope ts = new TypeAndScope();
		ts.type = currentScope.getIdentifierType(ident);
		ts.scope = currentScope.getIdentifierScope(ident);
		nextSym++;
		if (sym() == SymbolType.OP_PERIOD && ts.type == Type.IMPORTED_MODULE) {
			nextSym++;
			expect(SymbolType.IDENTIFIER, "identifier expected", false);
			String ident2 = symbol().getText();
			symbol().setLink(ts.scope.getIdentifierLink(ident2));
			ts.type = ts.scope.getIdentifierType(ident2);
			ts.scope = ts.scope.getIdentifierScope(ident2);
			nextSym++;
		}
		return ts;
	}

	private Scope parseType(boolean topLevel, String linkPrefix, boolean exported) {
		if (sym() == SymbolType.IDENTIFIER) {
			TypeAndScope ts = parseQualifiedIdentifier();
			if (ts.type != Type.TYPE)
				throw new RuntimeException("not a type or undefined");
			if (exported && ts.scope.getPublicScope() == null)
				throw new RuntimeException("exporting an alias of an unexported type");
			return ts.scope;
		} else if (sym() == SymbolType.KEYWORD_ARRAY) {
			nextSym++;
			parseExpression();
			while (sym() == SymbolType.OP_COMMA) {
				nextSym++;
				parseExpression();
			}
			expect(SymbolType.KEYWORD_OF, "missing OF", true);
			Scope elemTypeScope = parseType(topLevel, linkPrefix + ".ARRAY", false);
			Scope result = new Scope(null, linkPrefix, currentScope);
			result.putIdentifier("[]", Type.VARIABLE, elemTypeScope);
			if (exported) {
				Scope pubScope = new Scope(null, "@@", currentScope);
				result.setPublicScope(pubScope);
				pubScope.putIdentifier("[]", Type.VARIABLE, elemTypeScope.getPublicScope());
			}
			return result;
		} else if (sym() == SymbolType.KEYWORD_RECORD) {
			if (!topLevel || linkPrefix == null)
				throw new RuntimeException("Anonymous record types are not supported");
			nextSym++;
			Scope baseScope = null;
			if (sym() == SymbolType.OP_LPAREN) { // record extension
				nextSym++;
				expect(SymbolType.IDENTIFIER, "ident expected", false);
				TypeAndScope baseTypeScope = parseQualifiedIdentifier();
				if (baseTypeScope.type != Type.TYPE)
					throw new RuntimeException("type expected");
				baseScope = baseTypeScope.scope;
				expect(SymbolType.OP_RPAREN, "no )", true);
			}
			Scope resultScope = new Scope(baseScope, linkPrefix + ".", currentScope);
			resultScope.putIdentifier(".", Type.VARIABLE, resultScope);
			if (exported) {
				Scope pubScope = new Scope(baseScope == null ? null : baseScope.getPublicScope(), linkPrefix + ".", currentScope.getPublicScope());
				pubScope.putIdentifier(".", Type.VARIABLE, pubScope);
				resultScope.setPublicScope(pubScope);
			}
			while (sym() == SymbolType.IDENTIFIER) { // fields
				List<Symbol[]> flds = new ArrayList<>();
				while (sym() == SymbolType.IDENTIFIER) {
					Symbol s = symbol();
					nextSym++;
					if (sym() == SymbolType.OP_TIMES) {
						Scope pubScope = resultScope.getPublicScope();
						if (pubScope == null || !exported)
							throw new RuntimeException("remove asterisk");
						flds.add(new Symbol[] { s, symbol() });
						nextSym++;
					} else {
						flds.add(new Symbol[] { s });
					}
					if (sym() == SymbolType.OP_COMMA)
						nextSym++;
					else if (sym() != SymbolType.OP_COLON)
						throw new RuntimeException("comma expected");
				}
				expect(SymbolType.OP_COLON, "colon expected", true);
				Scope typeScope = parseType(topLevel, linkPrefix + "." + flds.get(0)[0], flds.stream().anyMatch(f -> f.length == 2));
				for (Symbol[] fld : flds) {
					String identifier = fld[0].getText();
					resultScope.putIdentifier(identifier, Type.VARIABLE, typeScope);
					setDefinitionLink(resultScope, fld[0]);
					if (fld.length == 2) { // exported symbol
						Scope pubScope = resultScope.getPublicScope();
						pubScope.putIdentifier(identifier, Type.VARIABLE, typeScope.getPublicScope());
						setExportUsageLink(resultScope, fld[1], identifier);
					}
				}
				if (sym() == SymbolType.OP_SEMICOLON)
					nextSym++;
				else if (sym() != SymbolType.KEYWORD_END)
					throw new RuntimeException(" ; or END");
			}
			expect(SymbolType.KEYWORD_END, "no END", true);
			return resultScope;
		} else if (sym() == SymbolType.KEYWORD_POINTER) {
			nextSym++;
			expect(SymbolType.KEYWORD_TO, "no TO", true);
			if (sym() == SymbolType.IDENTIFIER) {
				Scope result;
				String ident = symbol().getText();
				if (topLevel && !currentScope.isIdentifierDefined(ident)) {
					result = currentScope.makePointerScope(ident);
					symbol().setLink(result.getIdentifierLink("^").replaceFirst("\\.\\^$", ""));
					if (exported) {
						Scope pubPointerScope = currentScope.getPublicScope().makePointerScope(ident);
						result.setPublicScope(pubPointerScope);
					}
				} else {
					Type t = currentScope.getIdentifierType(ident);
					if (t == Type.TYPE) {
						Scope s = currentScope.getIdentifierScope(ident);
						result = new Scope(s, ident + ".", currentScope);
						result.putIdentifier("^", Type.VARIABLE, s);
						if (s.getPublicScope() != null) {
							Scope pub = new Scope(s.getPublicScope(), ident + ".", currentScope.getPublicScope());
							pub.putIdentifier("^", Type.VARIABLE, s.getPublicScope());
							result.setPublicScope(pub);
						}
					} else if (t == Type.IMPORTED_MODULE) {
						throw new RuntimeException("external base type not implemented");
					} else {
						throw new RuntimeException("no valid base type");
					}
					symbol().setLink(currentScope.getIdentifierLink(ident));
				}
				nextSym++;
				return result;
			} else {
				throw new RuntimeException("POINTER TO anything else than an identifier is not supported");
			}
		} else if (sym() == SymbolType.KEYWORD_PROCEDURE) {
			if (!topLevel || linkPrefix == null)
				throw new RuntimeException("Anonymous procedure types are not supported");
			nextSym++;
			Scope oldScope = currentScope;
			currentScope = new Scope(currentScope, linkPrefix + ".", currentScope);
			parseProcedureType(linkPrefix + ".PROCEDURE");
			currentScope = oldScope;
			Scope result = new Scope(null, linkPrefix + "()", currentScope);
			result.putIdentifier("()", Type.VARIABLE, new Scope(null, linkPrefix + "()()", currentScope));
			if (exported) {
				Scope pubScope = new Scope(null, "@@", currentScope);
				pubScope.putIdentifier("()", Type.VARIABLE, new Scope(null, "@@", currentScope));
				result.setPublicScope(pubScope);
			}
			return result;
		} else {
			throw new RuntimeException("Illegal type");
		}
	}

	private void parseIdentifierList(List<Symbol[]> identifiers) {
		while (sym() == SymbolType.IDENTIFIER) {
			Symbol s = symbol();
			nextSym++;
			if (sym() == SymbolType.OP_TIMES) {
				if (currentScope.getPublicScope() == null)
					throw new RuntimeException("remove asterisk");
				identifiers.add(new Symbol[] { s, symbol() });
				nextSym++;
			} else {
				identifiers.add(new Symbol[] { s });
			}
			if (sym() == SymbolType.OP_COMMA)
				nextSym++;
			else if (sym() != SymbolType.OP_COLON)
				throw new RuntimeException("comma?");
		}
		expect(SymbolType.OP_COLON, ":?", true);
	}

	private void parseSelector(TypeAndScope ts) {
		while (true) {
			if (sym() == SymbolType.OP_LBRAK) {
				if (ts.type != Type.VARIABLE || !ts.scope.isIdentifierDefined("[]"))
					throw new RuntimeException("not an array");
				ts.scope = ts.scope.getIdentifierScope("[]");
				do {
					nextSym++;
					parseExpression();
				} while (sym() == SymbolType.OP_COMMA);
				expect(SymbolType.OP_RBRAK, "no ]", true);
			} else if (sym() == SymbolType.OP_PERIOD) {
				nextSym++;
				expect(SymbolType.IDENTIFIER, "ident?", false);
				symbol().setLink(ts.scope.getIdentifierLink(symbol().getText()));
				ts.scope = ts.scope.getIdentifierScope(symbol().getText());
				nextSym++;
			} else if (sym() == SymbolType.OP_ARROW) {
				nextSym++;
				ts.scope = ts.scope.getIdentifierScope("^");
			} else if (sym() == SymbolType.OP_LPAREN && ts.type == Type.VARIABLE && ts.scope.isIdentifierDefined(".")) {
				nextSym++;
				expect(SymbolType.IDENTIFIER, "not an identifier", false);
				TypeAndScope guard = parseQualifiedIdentifier();
				if (guard.type != Type.TYPE)
					throw new RuntimeException("guard type expected");
				ts.scope = guard.scope;
				expect(SymbolType.OP_RPAREN, ") missing", true);
			} else {
				break;
			}
		}
	}

	private void parseParameterList() {
		nextSym++;
		while (sym() != SymbolType.OP_RPAREN) {
			parseExpression();
			if (sym() == SymbolType.OP_COMMA)
				nextSym++;
		}
		expect(SymbolType.OP_RPAREN, ") missing", true);
	}

	private void parseFactor() {
		if (sym() == SymbolType.IDENTIFIER) {
			TypeAndScope ts = parseQualifiedIdentifier();
			parseSelector(ts);
			if (sym() == SymbolType.OP_LPAREN) {
				parseParameterList();
			}
		} else if (sym() == SymbolType.OP_LPAREN) {
			nextSym++;
			parseExpression();
			expect(SymbolType.OP_RPAREN, "no )", true);
		} else if (sym() == SymbolType.OP_LBRACE) {
			nextSym++;
			if (sym() != SymbolType.OP_RBRACE) {
				parseExpression();
				if (sym() == SymbolType.OP_UPTO) {
					nextSym++;
					parseExpression();
				}
				while (!symbol().hasTypeOneOf(SymbolType.OP_RBRACE, SymbolType.OP_RBRAK, SymbolType.OP_RPAREN)) {
					if (sym() == SymbolType.OP_COMMA)
						nextSym++;
					else if (sym() != SymbolType.OP_RBRACE)
						throw new RuntimeException("missing comma");
					parseExpression();
					if (sym() == SymbolType.OP_UPTO) {
						nextSym++;
						parseExpression();
					}
				}
			}
			expect(SymbolType.OP_RBRACE, "no }", true);
		} else if (sym() == SymbolType.OP_NOT) {
			nextSym++;
			parseFactor();
		} else if (symbol().hasTypeOneOf(SymbolType.CONST, SymbolType.KEYWORD_NIL, SymbolType.KEYWORD_FALSE, SymbolType.KEYWORD_TRUE)) {
			nextSym++;
		} else {
			throw new RuntimeException("Not a factor");
		}
	}

	private static final SymbolType[] OPERATORS = { SymbolType.OP_AND, SymbolType.OP_MINUS, SymbolType.OP_PLUS, SymbolType.OP_RDIV, SymbolType.OP_TIMES, SymbolType.KEYWORD_DIV, SymbolType.KEYWORD_MOD, SymbolType.KEYWORD_OR, SymbolType.OP_EQL, SymbolType.OP_GEQ, SymbolType.OP_GTR, SymbolType.OP_LEQ, SymbolType.OP_LSS, SymbolType.OP_NEQ, SymbolType.KEYWORD_IN };

	private void parseExpression() {
		while (true) {
			if (symbol().hasTypeOneOf(SymbolType.OP_PLUS, SymbolType.OP_MINUS))
				nextSym++;
			parseFactor();
			if (!symbol().hasTypeOneOf(OPERATORS))
				break;
			nextSym++;
		}
		if (sym() == SymbolType.KEYWORD_IS) {
			nextSym++;
			TypeAndScope ts = parseQualifiedIdentifier();
			if (ts.type != Type.TYPE)
				throw new RuntimeException("not a type");
		}
	}

	private Scope parseFormalType(String linkPrefix) {
		if (sym() == SymbolType.IDENTIFIER) {
			TypeAndScope ts = parseQualifiedIdentifier();
			if (ts.type != Type.TYPE)
				throw new RuntimeException("not a type");
			return ts.scope;
		} else if (sym() == SymbolType.KEYWORD_ARRAY) {
			nextSym++;
			expect(SymbolType.KEYWORD_OF, "OF ?", true);
			Scope result = new Scope(null, linkPrefix, currentScope);
			result.putIdentifier("[]", Type.VARIABLE, parseFormalType(linkPrefix + ".ARRAY"));
			return result;
		} else if (sym() == SymbolType.KEYWORD_PROCEDURE) {
			nextSym++;
			Scope oldScope = currentScope;
			currentScope = new Scope(currentScope, linkPrefix, currentScope);
			parseProcedureType(linkPrefix + ".PROCEDURE");
			currentScope = oldScope;
			Scope result = new Scope(null, "@@", null);
			result.putIdentifier("()", Type.VARIABLE, new Scope(null, "@@", null));
			return result;
		} else {
			throw new RuntimeException("identifier expected");
		}
	}

	private void parseProcedureType(String linkPrefix) {
		if (sym() != SymbolType.OP_LPAREN)
			return;
		nextSym++;
		if (sym() == SymbolType.KEYWORD_VAR)
			nextSym++;
		while (sym() == SymbolType.IDENTIFIER) {
			List<Symbol[]> idents = new ArrayList<>();
			parseIdentifierList(idents);
			Scope typeScope = parseFormalType(linkPrefix);
			for (Symbol[] ident : idents) {
				if (ident.length == 2)
					throw new RuntimeException("Exported procedure parameter?");
				currentScope.putIdentifier(ident[0].getText(), Type.VARIABLE, typeScope);
				setDefinitionLink(currentScope, ident[0]);
			}
			if (sym() == SymbolType.OP_RPAREN)
				break;
			expect(SymbolType.OP_SEMICOLON, "no ;", true);
			if (sym() == SymbolType.KEYWORD_VAR)
				nextSym++;
		}
		expect(SymbolType.OP_RPAREN, "no )", true);
		if (sym() == SymbolType.OP_COLON) { // function
			nextSym++;
			expect(SymbolType.IDENTIFIER, "type identifier expected", false);
			TypeAndScope ts = parseQualifiedIdentifier();
			if (ts.type != Type.TYPE)
				throw new RuntimeException("illegal function type");
		}
	}

	private void parseStatementSequence() {
		while (true) {
			if (sym() == SymbolType.IDENTIFIER) {
				TypeAndScope ts = parseQualifiedIdentifier();
				parseSelector(ts);
				if (sym() == SymbolType.OP_BECOMES) {
					nextSym++;
					parseExpression();
				} else if (sym() == SymbolType.OP_EQL) {
					throw new RuntimeException("should be :=");
				} else if (sym() == SymbolType.OP_LPAREN) {
					parseParameterList();
				}
			} else if (sym() == SymbolType.KEYWORD_IF) {
				nextSym++;
				parseExpression();
				expect(SymbolType.KEYWORD_THEN, "no THEN", true);
				parseStatementSequence();
				while (sym() == SymbolType.KEYWORD_ELSIF) {
					nextSym++;
					parseExpression();
					expect(SymbolType.KEYWORD_THEN, "no THEN", true);
					parseStatementSequence();
				}
				if (sym() == SymbolType.KEYWORD_ELSE) {
					nextSym++;
					parseStatementSequence();
				}
				expect(SymbolType.KEYWORD_END, "no END", true);
			} else if (sym() == SymbolType.KEYWORD_WHILE) {
				nextSym++;
				parseExpression();
				expect(SymbolType.KEYWORD_DO, "no DO", true);
				parseStatementSequence();
				while (sym() == SymbolType.KEYWORD_ELSIF) {
					nextSym++;
					parseExpression();
					expect(SymbolType.KEYWORD_DO, "no DO", true);
					parseStatementSequence();
				}
				expect(SymbolType.KEYWORD_END, "no END", true);
			} else if (sym() == SymbolType.KEYWORD_REPEAT) {
				nextSym++;
				parseStatementSequence();
				expect(SymbolType.KEYWORD_UNTIL, "missing UNTIL", true);
				parseExpression();
			} else if (sym() == SymbolType.KEYWORD_FOR) {
				nextSym++;
				expect(SymbolType.IDENTIFIER, "identifier expected", false);
				parseQualifiedIdentifier();
				expect(SymbolType.OP_BECOMES, ":= expected", true);
				parseExpression();
				expect(SymbolType.KEYWORD_TO, "no TO", true);
				parseExpression();
				if (sym() == SymbolType.KEYWORD_BY) {
					nextSym++;
					parseExpression();
				}
				expect(SymbolType.KEYWORD_DO, "no DO", true);
				parseStatementSequence();
				expect(SymbolType.KEYWORD_END, "no END", true);
			} else if (sym() == SymbolType.KEYWORD_CASE) {
				nextSym++;
				expect(SymbolType.IDENTIFIER, "ident expected", false);
				String ident = symbol().getText();
				symbol().setLink(currentScope.getIdentifierLink(ident));
				if (currentScope.getIdentifierType(ident) != Type.VARIABLE)
					throw new RuntimeException("var expected");
				nextSym++;
				expect(SymbolType.KEYWORD_OF, "OF expected", true);
				while (sym() == SymbolType.IDENTIFIER) {
					TypeAndScope newTypeTS = parseQualifiedIdentifier();
					if (newTypeTS.type != Type.TYPE)
						throw new RuntimeException("type expected");
					expect(SymbolType.OP_COLON, ": expected", true);
					Scope oldScope = currentScope;
					currentScope = new Scope(currentScope, "", currentScope);
					currentScope.putIdentifier(ident, Type.VARIABLE, newTypeTS.scope);
					parseStatementSequence();
					currentScope = oldScope;
					if (sym() == SymbolType.KEYWORD_END) {
						break;
					}
					expect(SymbolType.OP_BAR, "no END", true);
				}
				expect(SymbolType.KEYWORD_END, "no END", true);
			}
			if (sym() == SymbolType.OP_SEMICOLON)
				nextSym++;
			else
				break;
		}
	}

	private void parseDeclarations() {
		if (sym() == SymbolType.KEYWORD_CONST) {
			nextSym++;
			while (sym() == SymbolType.IDENTIFIER) {
				String cnst = symbol().getText();
				currentScope.putIdentifier(cnst, Scope.Type.CONSTANT, null);
				setDefinitionLink(currentScope, symbol());
				nextSym++;
				checkExport(cnst);
				expect(SymbolType.OP_EQL, "= ?", true);
				parseExpression();
				expect(SymbolType.OP_SEMICOLON, "; missing", true);
			}
		}
		if (sym() == SymbolType.KEYWORD_TYPE) {
			nextSym++;
			while (sym() == SymbolType.IDENTIFIER) {
				Symbol symbol = symbol();
				String identifier = symbol.getText();
				nextSym++;
				Symbol exportSymbol = null;
				if (sym() == SymbolType.OP_TIMES) {
					if (currentScope.getPublicScope() == null)
						throw new RuntimeException("remove asterisk");
					exportSymbol = symbol();
					nextSym++;
				}
				expect(SymbolType.OP_EQL, "=?", true);
				Scope typeScope = parseType(true, identifier, exportSymbol != null);
				currentScope.putIdentifier(identifier, Type.TYPE, typeScope);
				if (exportSymbol != null) {
					Scope pubScope = currentScope.getPublicScope();
					Scope identPubScope = typeScope.getPublicScope();
					if (identPubScope == null)
						throw new RuntimeException("INTERNAL: Exported type declaration has no public scope");
					pubScope.putIdentifier(identifier, Type.TYPE, identPubScope);
					// public scope of public scope is itself; needed for
					// exported variables of imported types
					identPubScope.setPublicScope(identPubScope);
				}
				setDefinitionLink(currentScope, symbol);
				setExportUsageLink(currentScope, exportSymbol, identifier);
				expect(SymbolType.OP_SEMICOLON, "; missing", true);
			}
		}
		if (sym() == SymbolType.KEYWORD_VAR) {
			nextSym++;
			while (sym() == SymbolType.IDENTIFIER) {
				List<Symbol[]> vars = new ArrayList<>();
				parseIdentifierList(vars);
				Scope typeScope = parseType(true, vars.get(0)[0].getText(), vars.stream().anyMatch(v -> v.length == 2));
				for (Symbol[] var : vars) {
					String identifier = var[0].getText();
					currentScope.putIdentifier(identifier, Type.VARIABLE, typeScope);
					setDefinitionLink(currentScope, var[0]);
					if (var.length == 2) { // exported symbol
						Scope pubScope = currentScope.getPublicScope();
						pubScope.putIdentifier(identifier, Type.VARIABLE, typeScope.getPublicScope());
						setExportUsageLink(currentScope, var[1], identifier);
					}
				}
				expect(SymbolType.OP_SEMICOLON, "; missing", true);
			}
		}
	}

	protected void setExportUsageLink(Scope scope, Symbol exportSymbol, String identifier) {
		if (exportSymbol != null) {
			String link = scope.getPublicScope().getIdentifierLink(identifier);
			exportSymbol.setLink(link.replace(".html#", "-usage.html#"));
		}
	}

	private void parseProcedureDeclaration() {
		nextSym++;
		if (sym() == SymbolType.OP_TIMES) {
			// interrupt procedure
			nextSym++;
		}
		expect(SymbolType.IDENTIFIER, "identifier expected", false);
		String procid = symbol().getText();
		currentScope.putIdentifier(procid, Scope.Type.PROCEDURE, null);
		setDefinitionLink(currentScope, symbol());
		nextSym++;
		checkExport(procid);
		Scope oldScope = currentScope;
		currentScope = new Scope(currentScope, procid + ".", currentScope);
		parseProcedureType(procid + ".PROCEDURE");
		expect(SymbolType.OP_SEMICOLON, "no ;", true);
		parseDeclarations();
		while (sym() == SymbolType.KEYWORD_PROCEDURE) {
			parseProcedureDeclaration();
			expect(SymbolType.OP_SEMICOLON, "no ;", true);
		}
		if (sym() == SymbolType.KEYWORD_BEGIN) {
			nextSym++;
			parseStatementSequence();
		}
		if (sym() == SymbolType.KEYWORD_RETURN) {
			nextSym++;
			parseExpression();
		}
		currentScope = oldScope;
		expect(SymbolType.KEYWORD_END, "no END", true);
		expect(SymbolType.IDENTIFIER, "no proc id", false);
		if (!symbol().getText().equals(procid))
			throw new RuntimeException("no match");
		symbol().setLink(currentScope.getIdentifierLink(procid));
		nextSym++;
	}

	public String parseModule() {
		expect(SymbolType.KEYWORD_MODULE, "must start with MODULE", true);
		if (sym() == SymbolType.OP_TIMES)
			nextSym++;
		expect(SymbolType.IDENTIFIER, "identifier expected", false);
		String moduleName = symbol().getText();
		symbol().setLink("=MODULE");
		nextSym++;
		expect(SymbolType.OP_SEMICOLON, "no ;", true);
		if (sym() == SymbolType.KEYWORD_IMPORT) {
			nextSym++;
			while (sym() == SymbolType.IDENTIFIER) {
				String localName = symbol().getText(), importedModule = localName;
				symbol().setLink("=" + localName);
				nextSym++;
				if (sym() == SymbolType.OP_BECOMES) {
					nextSym++;
					expect(SymbolType.IDENTIFIER, "id expected", false);
					importedModule = symbol().getText();
					symbol().setLink(importedModule + ".html");
					nextSym++;
				}
				if (!publicScopes.containsKey(importedModule))
					throw new RuntimeException("Importing unknown module: " + importedModule);
				currentScope.putIdentifier(localName, Scope.Type.IMPORTED_MODULE, publicScopes.get(importedModule));
				if (sym() != SymbolType.OP_SEMICOLON)
					expect(SymbolType.OP_COMMA, "comma missing", true);
			}
			expect(SymbolType.OP_SEMICOLON, "no ;", true);
		}
		Scope pubScope = new Scope(null, moduleName + ".html#", null);
		currentScope.setPublicScope(pubScope);
		publicScopes.put(moduleName, pubScope);
		parseDeclarations();
		while (sym() == SymbolType.KEYWORD_PROCEDURE) {
			parseProcedureDeclaration();
			expect(SymbolType.OP_SEMICOLON, "no ;", true);
		}
		if (sym() == SymbolType.KEYWORD_BEGIN) {
			nextSym++;
			parseStatementSequence();
		}
		expect(SymbolType.KEYWORD_END, "no END", true);
		expect(SymbolType.IDENTIFIER, "identifier missing", false);
		if (!symbol().getText().equals(moduleName))
			throw new RuntimeException("no match");
		symbol().setLink("#MODULE");
		nextSym++;
		expect(SymbolType.OP_PERIOD, "period missing", false);
		return moduleName;
	}

	public static class TypeAndScope {
		public Type type;
		public Scope scope;
	}
}

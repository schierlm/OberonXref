if (!String.prototype.startsWith) {
  String.prototype.startsWith = function(searchString, position) {
    position = position || 0;
    return this.indexOf(searchString, position) === position;
  };
}

function unmarkSameLinks() {
	document.querySelectorAll("a.currusage, a.currdef").forEach(function(tag) {
		tag.classList.remove("currusage");
		tag.classList.remove("currdef");
	});
}

function markSameLinks() {
	unmarkSameLinks();
	var targetHref = this.href;
	document.querySelectorAll("a").forEach(function(tag) {
		if (tag.href == targetHref) {
			tag.classList.add(tag.name ? "currdef" : "currusage");
		}
	});
}

function undoCollapse(lineno) {
	var table = document.getElementsByTagName("table")[0];
	if (!table.classList.contains("collapseall"))
		return true;
	table.classList.remove("collapseall");
	document.getElementById("togglebuttons").classList.remove("collapseall");
	table.classList.add("showsource");
	document.querySelectorAll("tr.show").forEach(function(tag) {
		tag.classList.remove("show");
	});
	var controlSpan = document.getElementById("control");
	var origTag = controlSpan.firstChild;
	controlSpan.removeChild(origTag);
	controlSpan.parentNode.insertBefore(origTag, controlSpan);
	controlSpan.parentNode.removeChild(controlSpan);
	if (lineno)
		window.location.href="#L_"+lineno;
}

function uncollapseRow(node) {
	var row = node;
	while (row.nodeName != "TR" && row.nodeName != "BODY") {
		row = row.parentNode;
	}
	row.classList.add("show");
}

function collapseToReferences() {
	var table = document.getElementsByTagName("table")[0];
	if (!table.classList.contains("showsource"))
		return true;
	table.classList.remove("showsource");
	table.classList.add("collapseall");
	document.getElementById("togglebuttons").classList.add("collapseall");
	uncollapseRow(this);
	var targetHref = this.href;
	document.querySelectorAll("a").forEach(function(tag) {
		if (tag.href == targetHref) {
			uncollapseRow(tag);
		}
	});
	var controlSpan = document.createElement("span");
	var extraSpan = document.createElement("span");
	document.querySelectorAll("tr.show th a").forEach(function(tag) {
		extraSpan.innerHTML += " <a href='javascript:undoCollapse(\""+tag.dataset.l+"\")'>"+tag.dataset.l+"</a>";
	});
	extraSpan.innerHTML+=" <a href='javascript:undoCollapse()'>(Back)</a>";
	controlSpan.id="control"
	this.parentNode.insertBefore(controlSpan, this);
	this.parentNode.removeChild(this);
	controlSpan.appendChild(this);
	controlSpan.appendChild(extraSpan);
	return false;
}

function findLine(anchor) {
	var found = null;
	document.querySelectorAll("th a").forEach(function(tag) {
		if (tag.name == anchor)
			found = tag.parentNode.parentNode;
	});
	return found;
}

function updateLines() {
	document.querySelectorAll("tr.sel").forEach(function(tag) {
		tag.classList.remove("sel");
	});
	var hash = window.location.hash.substring(1);
	if (!hash.startsWith("L_") && !hash.startsWith("A_"))
		return;
	if (hash.indexOf("-") != -1) {
		var firstLine = findLine(hash.substring(0, hash.indexOf("-")));
		var lastLine = findLine(hash.substring(0, 2) + hash.substring(hash.indexOf("-")+1));
		if (firstLine != null && lastLine != null && firstLine.compareDocumentPosition(lastLine) & Node.DOCUMENT_POSITION_FOLLOWING) {
			firstLine.classList.add("sel");
			document.querySelectorAll("tr").forEach(function(tag) {
				if (tag.className == lastLine.className) {
					if (firstLine.compareDocumentPosition(tag) & Node.DOCUMENT_POSITION_FOLLOWING) {
						if (tag.compareDocumentPosition(lastLine) & Node.DOCUMENT_POSITION_FOLLOWING) {
							tag.classList.add("sel");
						}
					}
				}
			});
			lastLine.classList.add("sel");
		}
	} else {
		var line = findLine(hash);
		if (line != null)
			line.classList.add("sel");
	}
}

function toggleasm(elem) {
	var table = document.getElementsByTagName("table")[0];
	table.classList.remove("showsource");
	table.classList.remove("showassembly");
	table.classList.remove("showboth");
	table.classList.add(elem.value);
}

window.onhashchange = function() {
	if (window.location.hash.startsWith("#L_") || window.location.hash.startsWith("#A_"))
		updateLines();
};

window.onload = function() {
	if (document.getElementsByTagName("table")[0].classList.contains("assemblypresent")) {
		document.getElementById("togglebuttons").innerHTML='<label><input type="radio" checked="checked" name="asm" value="showsource" onclick="toggleasm(this)"> Source</label> <label><input id="asmradio" type="radio" name="asm" value="showassembly" onclick="toggleasm(this)"> Assembly</label> <label><input id="bothradio" type="radio" name="asm" value="showboth" onclick="toggleasm(this)"> Both</label>';
	}
	if (window.location.hash.startsWith("#L_"))
		updateLines();
	if (window.location.hash.startsWith("#A_")) {
		document.getElementById("asmradio").checked=true;
		toggleasm(document.getElementById("asmradio"));
		updateLines();	
	}
	if (window.location.hash == "#both") {
		document.getElementById("bothradio").checked=true;
		toggleasm(document.getElementById("bothradio"));
	}
	document.querySelectorAll("a").forEach(function(tag) {
		if (tag.parentNode.tagName == "TH") {
			tag.onclick = function() {
				setTimeout(updateLines, 1);
			};
		} else if (tag.name) {
			tag.onclick = collapseToReferences;
		}
		tag.onmouseover = markSameLinks;
		tag.onmouseout = unmarkSameLinks;
	});
};
function text(node){
  var result = []
  for (var i = 0; i < node.childNodes.length; i++) {
    c = node.childNodes[i]
    if (c.nodeType == 3) result.push(c.nodeValue)
    else result.push(text(c))
  }
  return result.join('')
}

function headings(level)
{
  var headingElements = document.getElementsByTagName("h"+level)
  result = []
  for (var i=0; i < headingElements.length; i++) {
    var element = headingElements[i]
    var elementText = text(element)
    result.push([elementText, element])
  }
  return result
}

function scrollToMatch(text) {
	var comparators = matcher.getNumberOfComparators();	
	for (var comparator = 0; comparator < comparators; comparator++) {
		for (var j=1; j <=6; j++) {
			var h = headings(j);
			for (var k=0; k < h.length; k++) {
				heading = h[k];
				headingText = heading[0];
				if (matcher.match(headingText, text, comparator)) {
				    myScrollTo(heading[1]);
					return;
				}
			}
		}		
	}
}	


function s(elementId) {
    myScrollTo(document.getElementById(elementId));    
    return false;
}

function myScrollTo(elem) {
    alert("scroll to " + elem);    
    var y = 0;
    while (elem != null) {
        y += elem.offsetTop;
        elem = elem.offsetParent;
    }
    alert("y = " + y);
    window.scrollTo(0, y);                 
}

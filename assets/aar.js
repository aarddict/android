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

function scrollToMatch(text, matchStrength) {
  for (var j=1; j <=6; j++) {
     var h = headings(j)
     for (var k=0; k < h.length; k++) {
       heading = h[k]
       headingText = heading[0]
       matcher.match(headingText, text, matchStrength)
       if (matcher.result) {
          headingElem = heading[1]
          headingElem.scrollIntoView(true)
          return true
       }
     }
  }
  return false
}

function s(elementId) {
    document.getElementById(elementId).scrollIntoView(true);
    return false;
}
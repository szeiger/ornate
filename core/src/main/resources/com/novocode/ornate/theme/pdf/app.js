$(window).load(function() {
  var triggersNeeded = 0;
  if(typeof mermaidAPI !== 'undefined')  {
    mermaidAPI.initialize({
      //logLevel: 1,
      startOnLoad: false,
      cloneCssStyles: false
    });
    $(".mermaid_src").each(function() {
      var srcE = this;
      var e = srcE.parentElement;
      var insertSvg = function(svgCode, bindFunctions) {
        //console.log("Rendered "+e.id);
        e.innerHTML = svgCode;
        bindFunctions(e);
      };
      //console.log("Rendering "+e.id);
      mermaidAPI.render(e.id + "_svg", srcE.textContent, insertSvg, "#"+e.id);
    });
    window.doneTrigger();
  }
});
$(window).bind("load", function() {
  window.doneTrigger();
});

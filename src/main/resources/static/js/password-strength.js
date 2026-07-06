// Password strength meter (Section 01 §4.3) — advisory only; server re-checks hard rules.
(function () {
  function score(pw, userTerms) {
    if (!pw) return { tier: 0, label: "" };
    var s = 0;
    if (pw.length >= 8) s++;
    if (pw.length >= 12) s++;
    if (/[A-Z]/.test(pw)) s++;
    if (/[0-9]/.test(pw)) s++;
    if (/[!@#$%^&*()_+\-=\[\]{}|;:'",.<>\/?]/.test(pw)) s++;
    var capped = false;
    (userTerms || []).forEach(function (t) {
      if (t && t.length >= 3 && pw.toLowerCase().indexOf(t.toLowerCase()) !== -1) capped = true;
    });
    var tier = s <= 2 ? 1 : s === 3 ? 2 : s === 4 ? 3 : 4;
    if (capped && tier > 2) tier = 2;   // contains username/email → no higher than "Fair"
    return { tier: tier, label: ["", "Weak", "Fair", "Good", "Strong"][tier] };
  }

  document.querySelectorAll("[data-strength-for]").forEach(function (meter) {
    var input = document.getElementById(meter.getAttribute("data-strength-for"));
    if (!input) return;
    var bar = meter.querySelector("div");
    var label = document.querySelector("[data-strength-label-for='" + input.id + "']");
    var terms = (meter.getAttribute("data-user-terms") || "").split(",");
    var colors = ["transparent", "#a63d40", "#c58f52", "#4a6741", "#3a5233"];
    input.addEventListener("input", function () {
      var r = score(input.value, terms);
      bar.style.width = (r.tier * 25) + "%";
      bar.style.background = colors[r.tier];
      if (label) label.textContent = r.label;
    });
  });

  // Visibility toggles (eye icon)
  document.querySelectorAll("[data-toggle-visibility]").forEach(function (btn) {
    btn.addEventListener("click", function (e) {
      e.preventDefault();
      var input = document.getElementById(btn.getAttribute("data-toggle-visibility"));
      if (input) input.type = input.type === "password" ? "text" : "password";
    });
  });
})();

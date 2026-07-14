/**
 * Renders a "what's this total made of" composition doughnut from a breakdown list shaped like
 * [{name, total, formattedTotal, color, colorDark}, ...] (see DashboardController#namedBreakdown).
 * Shared by every dashboard card that shows a bare total's composition instead of just the number.
 */
function renderNamedDonut(canvasId, breakdown) {
    var canvas = document.getElementById(canvasId);
    if (!canvas || !breakdown || breakdown.length === 0) return;
    var isDark = document.body.classList.contains('theme-dark');
    var labels = breakdown.map(function (b) { return b.name; });
    var values = breakdown.map(function (b) { return b.total; });
    var colors = breakdown.map(function (b) { return isDark ? b.colorDark : b.color; });
    var tooltipLabels = breakdown.map(function (b) { return b.formattedTotal; });
    new Chart(canvas, {
        type: 'doughnut',
        data: { labels: labels, datasets: [{ data: values, backgroundColor: colors, borderWidth: 0 }] },
        options: {
            responsive: false,
            cutout: '68%',
            plugins: {
                legend: { display: false },
                tooltip: {
                    callbacks: {
                        label: function (ctx) { return labels[ctx.dataIndex] + ': ' + tooltipLabels[ctx.dataIndex]; }
                    }
                }
            }
        }
    });
}

/**
 * Renders a compact semi-circle gauge (percent filled, colored by zone) — used for EMI burden,
 * insurance coverage-vs-recommended, and retirement readiness. Sized to sit inline in a normal
 * card, not as a large standalone panel.
 */
function renderGauge(canvasId, percent, color) {
    var canvas = document.getElementById(canvasId);
    if (!canvas) return;
    var clamped = Math.max(0, Math.min(100, percent));
    var isDark = document.body.classList.contains('theme-dark');
    new Chart(canvas, {
        type: 'doughnut',
        data: { datasets: [{ data: [clamped, 100 - clamped],
            backgroundColor: [color, isDark ? '#2a2b33' : '#ecedf1'], borderWidth: 0,
            circumference: 180, rotation: 270 }] },
        options: { responsive: false, cutout: '70%', plugins: { legend: { display: false }, tooltip: { enabled: false } }, events: [] }
    });
}

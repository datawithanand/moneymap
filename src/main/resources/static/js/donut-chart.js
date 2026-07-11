/**
 * Renders a "what's this total made of" composition doughnut from a breakdown list shaped like
 * [{name, total, formattedTotal, color, colorDark}, ...] (see DashboardController#namedBreakdown).
 * Shared by every dashboard card that shows a bare total's composition instead of just the number.
 */
function renderNamedDonut(canvasId, breakdown) {
    var canvas = document.getElementById(canvasId);
    if (!canvas || !breakdown || breakdown.length === 0) return;
    var isDark = document.body.classList.contains('theme-midnight');
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

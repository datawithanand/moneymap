document.addEventListener('DOMContentLoaded', function () {
    var canvas = document.getElementById('previewChart');
    if (!canvas || typeof Chart === 'undefined') return;
    new Chart(canvas, {
        type: 'line',
        data: {
            labels: ['', '', '', '', '', ''],
            datasets: [{
                data: [9, 10, 11, 12, 13, 17],
                borderColor: '#4338ca',
                backgroundColor: 'rgba(67,56,202,.08)',
                fill: true, tension: .35, pointRadius: 0, borderWidth: 2.5
            }]
        },
        options: {
            responsive: true, maintainAspectRatio: false,
            plugins: { legend: { display: false } },
            scales: { x: { display: false }, y: { display: false } }
        }
    });
});

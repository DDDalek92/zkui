/* Offline text sorting. Move existing rows to preserve controls and event handlers. */
(function () {
    'use strict';
    document.addEventListener('DOMContentLoaded', function () {
        Array.prototype.forEach.call(document.querySelectorAll('table[data-sortable]'), function (table) {
            Array.prototype.forEach.call(table.querySelectorAll('thead button[data-sort-column]'), function (button) {
                button.addEventListener('click', function () {
                    var header = button.parentNode;
                    var ascending = header.getAttribute('aria-sort') !== 'ascending';
                    var column = Number(button.getAttribute('data-sort-column'));
                    var body = table.tBodies[0];
                    var rows = Array.prototype.map.call(body.rows, function (row, index) {
                        return {row: row, index: index, text: row.cells[column].textContent.trim().toLowerCase()};
                    });
                    rows.sort(function (a, b) {
                        var compared = a.text < b.text ? -1 : (a.text > b.text ? 1 : 0);
                        return compared === 0 ? a.index - b.index : compared * (ascending ? 1 : -1);
                    });
                    Array.prototype.forEach.call(table.querySelectorAll('th[aria-sort]'), function (other) {
                        other.setAttribute('aria-sort', 'none');
                    });
                    header.setAttribute('aria-sort', ascending ? 'ascending' : 'descending');
                    rows.forEach(function (entry) { body.appendChild(entry.row); });
                });
            });
        });
    });
}());

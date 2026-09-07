(function () {
    const itemsBody = document.getElementById('items-body');
    const addItemBtn = document.getElementById('add-item');
    const template = document.getElementById('product-template');

    if (!itemsBody || !addItemBtn || !template) {
        return;
    }

    function reindexRows() {
        const rows = itemsBody.querySelectorAll('.item-row');
        rows.forEach((row, index) => {
            row.querySelectorAll('[name]').forEach((el) => {
                el.name = el.name.replace(/items\[\d+]/, 'items[' + index + ']');
            });
        });
    }

    function buildRow() {
        const row = document.createElement('tr');
        row.className = 'item-row';
        row.innerHTML =
            '<td><select name="items[0].productId" class="product-select">' + template.innerHTML + '</select></td>' +
            '<td><input type="number" min="1" value="1" name="items[0].quantity" class="quantity-input"/></td>' +
            '<td><input type="number" step="0.01" min="0.01" name="items[0].unitPrice" class="price-input"/></td>' +
            '<td><button type="button" class="btn btn-secondary remove-item">Remove</button></td>';
        return row;
    }

    addItemBtn.addEventListener('click', () => {
        itemsBody.appendChild(buildRow());
        reindexRows();
    });

    itemsBody.addEventListener('click', (event) => {
        if (event.target.classList.contains('remove-item')) {
            event.target.closest('.item-row').remove();
            reindexRows();
        }
    });

    itemsBody.addEventListener('change', (event) => {
        if (!event.target.classList.contains('product-select')) {
            return;
        }
        const row = event.target.closest('.item-row');
        const priceInput = row.querySelector('.price-input');
        const selectedOption = event.target.selectedOptions[0];
        const price = selectedOption ? selectedOption.getAttribute('data-price') : null;
        if (price && !priceInput.value) {
            priceInput.value = price;
        }
    });
})();

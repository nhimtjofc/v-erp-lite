jQuery(function ($) {
    var syncBtn = $('#verpc-sync-now-btn');
    var syncSpinner = $('#verpc-sync-spinner');
    var healthBtn = $('#verpc-check-health-btn');

    function setHealth(status) {
        var ind = $('#verpc-health-indicator');
        ind.removeClass('verpc-status-checking verpc-status-connected verpc-status-configured verpc-status-error verpc-status-disconnected verpc-status-disabled');
        if (status === 'CONNECTED') ind.addClass('verpc-status-connected');
        else if (status === 'CONFIGURED') ind.addClass('verpc-status-configured');
        else ind.addClass('verpc-status-error');
        ind.text(status);
    }

    healthBtn.on('click', function () {
        healthBtn.prop('disabled', true).text('Checking...');
        $.post(verpc_ajax.ajax_url, { action: 'verpc_check_health', _ajax_nonce: verpc_ajax.nonce },
        function (resp) {
            if (resp.success && resp.data) {
                setHealth(resp.data.status || 'UNKNOWN');
                $('#verpc-health-time').text(resp.data.lastHealthCheckedAt || new Date().toISOString());
            } else {
                setHealth('ERROR');
            }
        }).always(function () {
            healthBtn.prop('disabled', false).text('Check Health');
        });
    });

    syncBtn.on('click', function () {
        syncBtn.prop('disabled', true);
        syncSpinner.addClass('is-active');
        $.post(verpc_ajax.ajax_url, { action: 'verpc_sync_now', _ajax_nonce: verpc_ajax.nonce },
        function (resp) {
            if (resp.success && resp.data) {
                var d = resp.data;
                $('#verpc-last-sync').text(d.lastSync || '—');
                var $tbody = $('.verpc-results-table tbody');
                if (!$tbody.length) {
                    $('.verpc-card:eq(2) .verpc-card-body').empty().append(
                        '<table class="widefat striped verpc-results-table"><thead><tr>'
                        + '<th>SKU</th><th>Type</th><th>Status</th><th>Error</th><th>ERP ID</th><th>Woo ID</th>'
                        + '</tr></thead><tbody></tbody></table>');
                    $tbody = $('.verpc-results-table tbody');
                } else {
                    $tbody.empty();
                }
                $.each(d.results, function (i, r) {
                    $tbody.append('<tr class="verpc-result-' + (r.status || '').toLowerCase() + '">'
                        + '<td>' + (r.sku || '') + '</td>'
                        + '<td>' + (r.entityType || 'PRODUCT') + '</td>'
                        + '<td>' + (r.status || '') + '</td>'
                        + '<td>' + (r.error || '') + '</td>'
                        + '<td><code>' + (r.erpId || '') + '</code></td>'
                        + '<td><code>' + (r.externalId || '') + '</code></td></tr>');
                });
                alert(d.summary || 'Sync completed.');
            } else {
                alert('Sync failed: ' + (resp.data?.message || 'Unknown error'));
            }
        }).always(function () {
            syncBtn.prop('disabled', false);
            syncSpinner.removeClass('is-active');
        });
    });

    healthBtn.trigger('click');
});

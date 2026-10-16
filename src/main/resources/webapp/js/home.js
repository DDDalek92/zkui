/**
 #
 # Copyright (c) 2014, Deem Inc. All Rights Reserved.
 #
 # Licensed under the Apache License, Version 2.0 (the "License");
 # you may not use this file except in compliance with the License.
 # You may obtain a copy of the License at
 #
 # http://www.apache.org/licenses/LICENSE-2.0
 #
 # Unless required by applicable law or agreed to in writing, software
 # distributed under the License is distributed on an "AS IS" BASIS,
 # WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 # See the License for the specific language governing permissions and
 # limitations under the License.
 #
 */

'use strict';
// Only the modal, dropdown and property-editing behavior used by these templates.
// No HTML parser, selector interpolation, jQuery or Bootstrap plugin runtime.
document.addEventListener('DOMContentLoaded', function () {
    var activeModal = null, previousFocus = null, backdrop = null;
    function closeDropdowns() {
        document.querySelectorAll('.dropdown.open').forEach(function (item) {
            item.classList.remove('open');
            var toggle = item.querySelector('.dropdown-toggle');
            if (toggle) toggle.setAttribute('aria-expanded', 'false');
        });
    }
    function closeModal() {
        if (!activeModal) return;
        activeModal.classList.remove('in');
        activeModal.style.display = 'none';
        activeModal.setAttribute('aria-hidden', 'true');
        activeModal = null;
        document.body.classList.remove('modal-open');
        if (backdrop) backdrop.remove();
        backdrop = null;
        if (previousFocus && previousFocus.isConnected) previousFocus.focus();
        previousFocus = null;
    }
    function openModal(trigger) {
        var target = trigger.getAttribute('data-target');
        if (!target || !/^#[A-Za-z][A-Za-z0-9_-]*$/.test(target)) return;
        var modal = document.getElementById(target.substring(1));
        if (!modal || !modal.classList.contains('modal')) return;
        closeModal(); closeDropdowns();
        previousFocus = trigger; activeModal = modal;
        backdrop = document.createElement('div');
        backdrop.className = 'modal-backdrop fade in';
        document.body.appendChild(backdrop);
        document.body.classList.add('modal-open');
        modal.style.display = 'block';
        modal.classList.add('in');
        modal.setAttribute('aria-hidden', 'false');
        modal.focus();
    }
    document.addEventListener('click', function (event) {
        if (!(event.target instanceof Element)) return;
        var target = event.target;
        var view = target.closest('#importFileView');
        if (view) {
            try {
                var address = new URL(document.getElementById('scmServer').value
                        + document.getElementById('scmFileRevision').value + '@'
                        + document.getElementById('scmFilePath').value);
                if (address.protocol !== 'http:' && address.protocol !== 'https:') throw new Error('Invalid protocol');
                view.href = address.href;
                view.rel = 'noopener noreferrer';
            } catch (ignored) { event.preventDefault(); }
        }
        var selected = target.closest('.href-select');
        if (selected) {
            document.getElementById('newProperty').readOnly = true;
            document.getElementById('newProperty').value = selected.textContent;
            document.getElementById('newValue').value = selected.getAttribute('itemprop') || '';
            document.getElementById('savePropertyBtn').style.display = 'none';
            document.getElementById('updatePropertyBtn').style.display = '';
        }
        if (target.closest('#addPropertyBtn')) {
            document.getElementById('newProperty').readOnly = false;
            document.getElementById('updatePropertyBtn').style.display = 'none';
            document.getElementById('savePropertyBtn').style.display = '';
        }
        var opener = target.closest('[data-toggle="modal"]');
        if (opener) { event.preventDefault(); openModal(opener); return; }
        if (target.closest('[data-dismiss="modal"]') || target === activeModal) {
            event.preventDefault(); closeModal(); return;
        }
        var toggle = target.closest('.dropdown-toggle');
        if (toggle) {
            event.preventDefault();
            var dropdown = toggle.closest('.dropdown');
            var opened = dropdown.classList.contains('open');
            closeDropdowns();
            if (!opened) { dropdown.classList.add('open'); toggle.setAttribute('aria-expanded', 'true'); }
        } else if (!target.closest('.dropdown-menu')) closeDropdowns();
    });
    document.addEventListener('keydown', function (event) {
        if (event.key === 'Escape') {
            closeModal(); closeDropdowns();
        } else if (event.key === 'Tab' && activeModal) {
            var focusable = Array.from(activeModal.querySelectorAll('a[href], button, input, textarea, select, [tabindex]'))
                    .filter(function (item) { return !item.disabled && item.tabIndex >= 0 && item.getClientRects().length; });
            if (!focusable.length) { event.preventDefault(); activeModal.focus(); return; }
            var first = focusable[0], last = focusable[focusable.length - 1];
            if (event.shiftKey && (document.activeElement === first || document.activeElement === activeModal)) {
                event.preventDefault(); last.focus();
            } else if (!event.shiftKey && (document.activeElement === last || document.activeElement === activeModal)) {
                event.preventDefault(); first.focus();
            }
        }
    });
});

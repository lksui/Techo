/*
 * Techo — 前端交互。
 *
 * 只做几件 htmx 属性表达不了的事：
 *   1. 出错时把原因显示出来（htmx 默认不渲染 4xx/5xx 的响应内容）
 *   2. 左划手势揭示操作按钮
 *   3. 点击待办展开 / 收起子目录
 *   4. 子目录的就地输入框（加一条 / 改文字）
 *
 * 其余全部靠 htmx 的属性完成，没有手写的 DOM 拼装。
 */
(function () {
    'use strict';

    /* ============================================================ 出错提示 */

    function toast(text) {
        var el = document.getElementById('toast');
        if (!el || !text) {
            return;
        }
        el.textContent = text;
        el.classList.add('show');
        clearTimeout(el.dataset.timer);
        el.dataset.timer = setTimeout(function () {
            el.classList.remove('show');
        }, 4000);
    }

    document.addEventListener('htmx:responseError', function (event) {
        var xhr = event.detail.xhr;
        var text = (xhr.responseText || '').trim();
        toast(text || ('请求失败（HTTP ' + xhr.status + '）'));
    });

    document.addEventListener('htmx:sendError', function () {
        toast('连不上服务器，请确认手机和电脑在同一个 WiFi');
    });

    document.addEventListener('htmx:timeout', function () {
        toast('请求超时');
    });

    /* ============================================================ 左划手势 */

    /* 触发阈值。太小会误触，太大又划不动 */
    var SWIPE_TRIGGER = 40;

    var swipeStart = null;

    function closeSwipes(except) {
        document.querySelectorAll('.swiped-open').forEach(function (el) {
            if (el !== except) {
                el.classList.remove('swiped-open');
            }
        });
    }

    document.addEventListener('touchstart', function (event) {
        var clip = event.target.closest('.row-clip');
        if (!clip) {
            swipeStart = null;
            return;
        }
        var touch = event.touches[0];
        swipeStart = { clip: clip, x: touch.clientX, y: touch.clientY };
    }, { passive: true });

    document.addEventListener('touchend', function (event) {
        if (!swipeStart) {
            return;
        }
        var touch = event.changedTouches[0];
        var dx = touch.clientX - swipeStart.x;
        var dy = touch.clientY - swipeStart.y;
        var item = swipeStart.clip.parentElement;
        swipeStart = null;

        // 只认「明显偏水平」的滑动，否则当成纵向滚动，不干扰页面
        if (Math.abs(dx) < SWIPE_TRIGGER || Math.abs(dx) < Math.abs(dy) * 1.5) {
            return;
        }
        if (dx < 0) {
            closeSwipes(item);
            item.classList.add('swiped-open');
        } else {
            item.classList.remove('swiped-open');
        }
    }, { passive: true });

    document.addEventListener('touchcancel', function () {
        swipeStart = null;
    }, { passive: true });

    /* ============================================================ 子目录输入框 */

    /**
     * 收起就地输入框。
     * 作用域是整个 .todo-item —— 父待办的「加到末尾」和每条子目录的「插入 / 编辑」
     * 都在里面，同一时刻只该有一个打开。
     */
    function hideForms(el) {
        var scope = el.closest('.todo-item') || el;
        scope.querySelectorAll('.subtask-new-row').forEach(function (row) {
            row.hidden = true;
        });
        scope.querySelectorAll('.subtask-new-form, .subtask-edit-form').forEach(function (form) {
            form.hidden = true;
            var input = form.querySelector('input[name="body"]');
            // 编辑框还原成原始文字，避免上次按 Esc 取消后残留内容
            if (input && form.classList.contains('subtask-edit-form')) {
                input.value = input.defaultValue;
            }
        });
    }

    function revealForm(target) {
        if (!target) {
            return;
        }
        target.hidden = false;
        var input = target.querySelector('input[name="body"]');
        if (input) {
            input.focus();
            // 光标放到末尾，改错字时不用再点一下
            input.setSelectionRange(input.value.length, input.value.length);
        }
    }

    /** 新增 / 编辑子目录时要先把父待办展开，否则输入框在收起区域里看不见。 */
    function expandParentTodo(el) {
        var todo = el.closest('.todo-item');
        if (todo) {
            todo.classList.remove('collapsed');
            if (window.techoCollapse) {
                window.techoCollapse.remember(todo, false);
            }
        }
    }

    /* ============================================================ 点击 */

    document.addEventListener('click', function (event) {
        // 点待办文字：展开 / 收起子目录
        var toggle = event.target.closest('[data-toggle-subtasks]');
        if (toggle) {
            var todo = toggle.closest('.todo-item');
            if (todo && window.techoCollapse) {
                window.techoCollapse.toggle(todo);
            }
            closeSwipes(null);
            return;
        }

        // 点绿色回车：加一条
        var addBtn = event.target.closest('[data-subtask-add]');
        if (addBtn) {
            event.preventDefault();
            var addHost = addBtn.closest('.todo-item, .subtask');
            if (addHost) {
                hideForms(addHost);
                revealForm(addHost.classList.contains('subtask')
                        ? addHost.querySelector('.subtask-new-form')
                        : addHost.querySelector('.subtask-new-row'));
                expandParentTodo(addHost);
            }
            closeSwipes(null);
            return;
        }

        // 点铅笔：改文字
        var editBtn = event.target.closest('[data-subtask-edit]');
        if (editBtn) {
            event.preventDefault();
            var sub = editBtn.closest('.subtask');
            if (sub) {
                hideForms(sub);
                revealForm(sub.querySelector('.subtask-edit-form'));
                expandParentTodo(sub);
            }
            closeSwipes(null);
            return;
        }

        // 点空白处：收起已经划开的行
        if (!event.target.closest('.row-actions')) {
            closeSwipes(null);
        }
    });

    /* ============================================================ 键盘 */

    document.addEventListener('keydown', function (event) {
        if (event.key === 'Escape') {
            closeSwipes(null);
            // 顶层的 hideForms 需要一个元素作为起点，用 body 即可
            hideForms(document.body);
        }
    });

    /* ============================================================ htmx 之后 */

    document.addEventListener('htmx:afterSwap', function (event) {
        if (window.techoCollapse) {
            window.techoCollapse.apply(event.target);
        }

        // 刚加进来的子目录要把父待办展开，否则用户看不到自己加的东西
        document.querySelectorAll('.subtask.htmx-added').forEach(function (subtask) {
            var todo = subtask.closest('.todo-item');
            if (todo) {
                todo.classList.remove('collapsed');
                if (window.techoCollapse) {
                    window.techoCollapse.remember(todo, false);
                }
            }
        });
    });
})();

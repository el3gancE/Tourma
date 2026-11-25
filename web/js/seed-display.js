(function () {
    const unsaved = new Map();
    const keyFor = id => 'tourma_hide_seed_config_' + id;

    window.TourmaSeedDisplay = {
        read(id) {
            const key = keyFor(id);
            if (unsaved.has(key)) return unsaved.get(key);
            try {
                const value = sessionStorage.getItem(key);
                if (value !== null) return JSON.parse(value);
            } catch (ignored) {}
            try {
                return JSON.parse(localStorage.getItem(key));
            } catch (ignored) {
                return null;
            }
        },
        save(id, config) {
            const key = keyFor(id);
            const value = JSON.stringify(config);
            try {
                localStorage.setItem(key, value);
            } catch (error) {
                try {
                    sessionStorage.setItem(key, value);
                    unsaved.delete(key);
                } catch (ignored) {
                    unsaved.set(key, config);
                }
                return;
            }
            try {
                sessionStorage.removeItem(key);
                unsaved.delete(key);
            } catch (ignored) {
                unsaved.set(key, config);
            }
        }
    };
})();

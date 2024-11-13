import {registry} from '@jahia/ui-extender';
import register from './ForcePublishAll/register';

export default function () {
    registry.add('callback', 'forcePublishAll', {
        targets: ['jahiaApp-init:50'],
        callback: register
    });
}

console.debug('%c Force publish all is activated', 'color: #3c8cba');

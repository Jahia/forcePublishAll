import React from 'react';
import {
        registry
        } from '@jahia/ui-extender';
import {CloudUpload} from "@jahia/moonstone";
import {ForcePublishAllActionComponent} from "./ForcePublishAllComponent";

export default function () {

    registry.add('action', 'forcePublishAll', {
        buttonIcon: <CloudUpload/>,
        buttonLabel: 'Force Publish All',
        buttonLabelShort: 'Force Publish All',
        targets: ['publishMenu:99'],
        component: ForcePublishAllActionComponent
    });
}

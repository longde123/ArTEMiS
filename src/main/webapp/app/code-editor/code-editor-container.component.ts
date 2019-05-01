import { ComponentCanDeactivate } from 'app/shared';
import { HostListener, ViewChild, OnDestroy } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Observable, Subscription } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';

import { CodeEditorComponent } from './';
import { ParticipationDataProvider } from 'app/course-list';
import { ParticipationService, Participation } from 'app/entities/participation';
import { ActivatedRoute } from '@angular/router';

export abstract class CodeEditorContainer implements OnDestroy, ComponentCanDeactivate {
    @ViewChild(CodeEditorComponent) editor: CodeEditorComponent;
    paramSub: Subscription;

    constructor(
        protected participationService: ParticipationService,
        private participationDataProvider: ParticipationDataProvider,
        private translateService: TranslateService,
        protected route: ActivatedRoute,
    ) {}

    /**
     * The user will be warned if there are unsaved changes when trying to leave the code-editor.
     */
    canDeactivate() {
        return this.editor.hasUnsavedChanges();
    }

    // displays the alert for confirming refreshing or closing the page if there are unsaved changes
    @HostListener('window:beforeunload', ['$event'])
    unloadNotification($event: any) {
        if (!this.canDeactivate()) {
            $event.returnValue = this.translateService.instant('pendingChanges');
        }
    }

    /**
     * Try to retrieve the participation from cache, otherwise do a REST call to fetch it with the latest result.
     * @param participationId
     */
    protected loadParticipation(participationId: number): Observable<Participation | null> {
        if (this.participationDataProvider.participationStorage && this.participationDataProvider.participationStorage.id === participationId) {
            return Observable.of(this.participationDataProvider.participationStorage);
        } else {
            return this.participationService.findWithLatestResult(participationId).pipe(
                catchError(() => Observable.of(null)),
                map(res => res && res.body),
                tap(participation => (this.participationDataProvider.participationStorage = participation)),
            );
        }
    }

    ngOnDestroy() {
        if (this.paramSub) {
            this.paramSub.unsubscribe();
        }
    }
}
